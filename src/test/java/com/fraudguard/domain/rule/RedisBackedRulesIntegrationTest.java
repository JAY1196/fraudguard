package com.fraudguard.domain.rule;

import com.fraudguard.config.FraudGuardProperties;
import com.fraudguard.domain.engine.RuleEngine;
import com.fraudguard.domain.model.EvaluationResult;
import com.fraudguard.domain.model.RiskDecision;
import com.fraudguard.domain.model.Transaction;
import com.fraudguard.domain.model.TransactionType;
import com.fraudguard.domain.store.RedisStateStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisBackedRulesIntegrationTest {

    private static final UUID SOURCE_WALLET =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DESTINATION_WALLET =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final long TTL_SECONDS = 600L;

    private static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisStateStore stateStore;

    @BeforeAll
    static void startRedis() {
        redis.start();

        connectionFactory = new LettuceConnectionFactory(
                redis.getHost(),
                redis.getMappedPort(6379)
        );
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
        redisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushDb();
    }

    @Test
    @DisplayName("Velocity triggers only after the configured maximum is exceeded")
    void shouldRespectVelocityBoundaryWithRealRedis() {
        RuleEngine engine = createEngine(true, false);
        Instant baseTime = Instant.parse("2026-11-09T10:00:00Z");

        EvaluationResult fifthResult = null;

        for (int i = 1; i <= 5; i++) {
            fifthResult = engine.evaluate(
                    transaction(
                            UUID.randomUUID(),
                            10_000L,
                            baseTime.plusSeconds(i),
                            TransactionType.TRANSFER
                    )
            );
        }

        assertThat(fifthResult).isNotNull();
        assertThat(fifthResult.score()).isZero();
        assertThat(fifthResult.decision()).isEqualTo(RiskDecision.ALLOW);

        EvaluationResult sixthResult = engine.evaluate(
                transaction(
                        UUID.randomUUID(),
                        10_000L,
                        baseTime.plusSeconds(6),
                        TransactionType.TRANSFER
                )
        );

        assertThat(sixthResult.score()).isEqualTo(40);
        assertThat(sixthResult.reasonCodes()).contains("VELOCITY_EXCEEDED");
        assertThat(sixthResult.decision()).isEqualTo(RiskDecision.REVIEW);
    }

    @Test
    @DisplayName("Amount Spike does not trigger with exactly two prior transactions")
    void shouldNotTriggerAmountSpikeWithTwoPriorTransactions() {
        RuleEngine engine = createEngine(false, true);
        Instant baseTime = Instant.parse("2026-11-09T10:00:00Z");

        seedAmountHistory(engine, baseTime, 2);

        EvaluationResult result = engine.evaluate(
                transaction(
                        UUID.randomUUID(),
                        30_001L,
                        baseTime.plusSeconds(3),
                        TransactionType.TRANSFER
                )
        );

        assertThat(result.score()).isZero();
        assertThat(result.reasonCodes()).doesNotContain("AMOUNT_SPIKE");
        assertThat(result.decision()).isEqualTo(RiskDecision.ALLOW);
    }

    @Test
    @DisplayName("Amount Spike triggers with exactly three prior transactions")
    void shouldTriggerAmountSpikeWithThreePriorTransactions() {
        RuleEngine engine = createEngine(false, true);
        Instant baseTime = Instant.parse("2026-11-09T10:00:00Z");

        seedAmountHistory(engine, baseTime, 3);

        EvaluationResult result = engine.evaluate(
                transaction(
                        UUID.randomUUID(),
                        30_001L,
                        baseTime.plusSeconds(4),
                        TransactionType.TRANSFER
                )
        );

        assertThat(result.score()).isEqualTo(35);
        assertThat(result.reasonCodes()).contains("AMOUNT_SPIKE");
        assertThat(result.decision()).isEqualTo(RiskDecision.ALLOW);
    }

    @Test
    @DisplayName("Replaying the same transaction returns an identical evaluation result")
    void shouldReturnIdenticalEvaluationResultForExactReplay() {
        RuleEngine engine = createEngine(true, true);
        Instant baseTime = Instant.parse("2026-11-09T10:00:00Z");

        seedAmountHistory(engine, baseTime, 3);

        Transaction current = transaction(
                UUID.randomUUID(),
                30_001L,
                baseTime.plusSeconds(4),
                TransactionType.TRANSFER
        );

        EvaluationResult firstResult = engine.evaluate(current);
        EvaluationResult replayResult = engine.evaluate(current);

        assertThat(firstResult).isEqualTo(replayResult);
        assertThat(replayResult.score()).isEqualTo(35);
        assertThat(replayResult.decision()).isEqualTo(RiskDecision.ALLOW);
        assertThat(replayResult.reasonCodes()).contains("AMOUNT_SPIKE");
        assertThat(replayResult.degraded()).isFalse();
    }

    @Test
    @DisplayName("A late Velocity event does not delete newer events")
    void shouldPreserveNewerVelocityEventsWhenLateEventArrives() {
        RuleEngine engine = createEngine(true, false);

        String redisKey = "fraudguard:velocity:" + SOURCE_WALLET;
        UUID newerEventId = UUID.randomUUID();
        UUID lateEventId = UUID.randomUUID();

        engine.evaluate(
                transaction(
                        newerEventId,
                        10_000L,
                        Instant.ofEpochMilli(100_000L),
                        TransactionType.TRANSFER
                )
        );

        engine.evaluate(
                transaction(
                        lateEventId,
                        10_000L,
                        Instant.ofEpochMilli(60_000L),
                        TransactionType.TRANSFER
                )
        );

        assertThat(redisTemplate.opsForZSet().score(redisKey, newerEventId.toString()))
                .isEqualTo(100_000.0);

        assertThat(redisTemplate.opsForZSet().score(redisKey, lateEventId.toString()))
                .isEqualTo(60_000.0);

        assertThat(redisTemplate.opsForZSet().zCard(redisKey))
                .isEqualTo(2L);
    }

    private RuleEngine createEngine(boolean velocityEnabled, boolean amountSpikeEnabled) {
        FraudGuardProperties properties = new FraudGuardProperties(
                new FraudGuardProperties.ThresholdProperties(40, 70),
                new FraudGuardProperties.RulesProperties(
                        new FraudGuardProperties.HighAmountRuleProperties(
                                false, 5_000_000L, 50, "HIGH_AMOUNT"
                        ),
                        new FraudGuardProperties.UnusualHoursRuleProperties(
                                false, 23, 5, 2_000_000L, 40,
                                "UNUSUAL_HOURS_HIGH_AMOUNT", "Asia/Kolkata"
                        ),
                        new FraudGuardProperties.VelocityRuleProperties(
                                velocityEnabled, 300, 5, 40,
                                "VELOCITY_EXCEEDED", TTL_SECONDS
                        ),
                        new FraudGuardProperties.AmountSpikeRuleProperties(
                                amountSpikeEnabled, 3.0, 5, 3, 35,
                                "AMOUNT_SPIKE", 100, 86_400
                        )
                ),
                null
        );

        return new RuleEngine(
                List.of(
                        new VelocityRule(properties, stateStore),
                        new AmountSpikeRule(properties, stateStore)
                ),
                properties,
                Clock.fixed(
                        Instant.parse("2026-11-09T10:00:00Z"),
                        ZoneOffset.UTC
                )
        );
    }

    private void seedAmountHistory(RuleEngine engine, Instant baseTime, int count) {
        for (int i = 1; i <= count; i++) {
            engine.evaluate(
                    transaction(
                            UUID.randomUUID(),
                            10_000L,
                            baseTime.plusSeconds(i),
                            TransactionType.TRANSFER
                    )
            );
        }
    }

    private Transaction transaction(
            UUID eventId,
            long amountPaise,
            Instant timestamp,
            TransactionType type
    ) {
        return new Transaction(
                eventId,
                SOURCE_WALLET,
                DESTINATION_WALLET,
                amountPaise,
                type,
                "INR",
                timestamp
        );
    }
}