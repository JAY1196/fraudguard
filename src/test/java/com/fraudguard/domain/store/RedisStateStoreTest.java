package com.fraudguard.domain.store;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisStateStoreTest {

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisStateStore stateStore;

    @BeforeAll
    static void startRedis() {
        redis.start();
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        stateStore = new RedisStateStore(redisTemplate);
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redis.isRunning()) {
            redis.stop();
        }
    }

    @BeforeEach
    void clearRedis() {
        if (redisTemplate.getConnectionFactory() != null) {
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        }
    }

    @Test
    @DisplayName("Sliding window: counts events within window and prunes expired events")
    void shouldCountEventsAndPruneExpired() {
        String key = "fraudguard:velocity:" + UUID.randomUUID();
        long windowMs = 60_000L; // 1 minute
        long ttlSeconds = 120L;

        // t = 0
        long count1 = stateStore.recordAndCountSlidingWindow(key, "evt-1", 10_000L, windowMs, ttlSeconds);
        assertThat(count1).isEqualTo(1L);

        // t = +30s (within window)
        long count2 = stateStore.recordAndCountSlidingWindow(key, "evt-2", 40_000L, windowMs, ttlSeconds);
        assertThat(count2).isEqualTo(2L);

        // t = +75s (from t=0, so t=10_000 is 10_000 to 85_000: diff is 75_000 > 60_000 -> evt-1 pruned)
        long count3 = stateStore.recordAndCountSlidingWindow(key, "evt-3", 85_000L, windowMs, ttlSeconds);
        assertThat(count3).isEqualTo(2L); // evt-2 (40k) + evt-3 (85k), evt-1 (10k) was pruned
    }

    @Test
    @DisplayName("Idempotency: re-evaluating the same event_id does not double-count")
    void shouldNotDoubleCountDuplicateEventId() {
        String key = "fraudguard:velocity:" + UUID.randomUUID();
        long windowMs = 60_000L;
        long ttlSeconds = 120L;

        long count1 = stateStore.recordAndCountSlidingWindow(key, "evt-dup-1", 10_000L, windowMs, ttlSeconds);
        assertThat(count1).isEqualTo(1L);

        // Resubmit exact same event_id and timestamp
        long count2 = stateStore.recordAndCountSlidingWindow(key, "evt-dup-1", 10_000L, windowMs, ttlSeconds);
        assertThat(count2).isEqualTo(1L);
    }

    @Test
    @DisplayName("Out-of-order events: earlier event within window is properly inserted and counted")
    void shouldHandleOutOfOrderEventsWithinWindow() {
        String key = "fraudguard:velocity:" + UUID.randomUUID();
        long windowMs = 60_000L;
        long ttlSeconds = 120L;

        // Later event arrives first
        stateStore.recordAndCountSlidingWindow(key, "evt-later", 50_000L, windowMs, ttlSeconds);

        // Out-of-order earlier event arrives second (within 60s window of 50_000)
        long count = stateStore.recordAndCountSlidingWindow(key, "evt-earlier", 20_000L, windowMs, ttlSeconds);
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("Per-wallet isolation: transactions on wallet A do not leak into wallet B")
    void shouldIsolateBetweenWallets() {
        String keyA = "fraudguard:velocity:" + UUID.randomUUID();
        String keyB = "fraudguard:velocity:" + UUID.randomUUID();
        long windowMs = 60_000L;
        long ttlSeconds = 120L;

        stateStore.recordAndCountSlidingWindow(keyA, "evt-a1", 10_000L, windowMs, ttlSeconds);
        stateStore.recordAndCountSlidingWindow(keyA, "evt-a2", 20_000L, windowMs, ttlSeconds);

        long countB = stateStore.recordAndCountSlidingWindow(keyB, "evt-b1", 25_000L, windowMs, ttlSeconds);
        assertThat(countB).isEqualTo(1L);
    }

    @Test
    @DisplayName("TTL: key TTL is set and positive")
    void shouldSetTtlOnKey() {
        String key = "fraudguard:velocity:" + UUID.randomUUID();
        stateStore.recordAndCountSlidingWindow(key, "evt-1", 10_000L, 60_000L, 120L);

        Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(expire).isNotNull();
        assertThat(expire).isGreaterThan(0L).isLessThanOrEqualTo(120L);
    }

    @Test
    @DisplayName("Concurrency: 100 parallel evaluations for one wallet yield exact count of 100")
    void shouldAccuratelyCount100ParallelEvaluations() throws Exception {
        String key = "fraudguard:velocity:" + UUID.randomUUID();
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(16);

        try {
            List<Callable<Long>> tasks = new ArrayList<>(threadCount);
            long baseTime = 1_000_000L;
            long windowMs = 300_000L; // 5 minutes

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                tasks.add(() -> stateStore.recordAndCountSlidingWindow(
                        key, "evt-concurrent-" + idx, baseTime + idx, windowMs, 600L
                ));
            }

            List<Future<Long>> futures = executor.invokeAll(tasks);
            for (Future<Long> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            // Final count in the sorted set must be exactly 100
            Long finalCard = redisTemplate.opsForZSet().zCard(key);
            assertThat(finalCard).isEqualTo(100L);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("Amount history: records amounts, preserves order, and trims beyond maxHistory")
    void shouldRecordAndRetrieveRecentAmounts() {
        String key = "fraudguard:amounts:" + UUID.randomUUID();
        int maxHistory = 3;
        long ttlSeconds = 300L;

        stateStore.recordAmount(key, "evt-1", 100_000L, 1000L, maxHistory, ttlSeconds);
        stateStore.recordAmount(key, "evt-2", 200_000L, 2000L, maxHistory, ttlSeconds);
        stateStore.recordAmount(key, "evt-3", 300_000L, 3000L, maxHistory, ttlSeconds);

        List<Long> amounts = stateStore.getRecentAmounts(key);
        assertThat(amounts).containsExactly(100_000L, 200_000L, 300_000L);

        // Add 4th amount -> oldest (100_000) must be trimmed
        stateStore.recordAmount(key, "evt-4", 400_000L, 4000L, maxHistory, ttlSeconds);
        List<Long> trimmed = stateStore.getRecentAmounts(key);
        assertThat(trimmed).containsExactly(200_000L, 300_000L, 400_000L);
    }

    @Test
    @DisplayName("Health check: reports true when Redis is reachable")
    void shouldReportHealthy() {
        assertThat(stateStore.isHealthy()).isTrue();
    }
}
