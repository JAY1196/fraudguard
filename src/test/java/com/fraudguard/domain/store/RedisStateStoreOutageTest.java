package com.fraudguard.domain.store;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RedisStateStoreOutageTest {

    @Container
    private static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static RedisStateStore stateStore;

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(
                redis.getHost(),
                redis.getMappedPort(6379)
        );
        connectionFactory.afterPropertiesSet();

        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        stateStore = new RedisStateStore(redisTemplate);
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void shouldReportUnhealthyAndPropagateConnectionFailureWhenRedisStops() {
        assertThat(stateStore.isHealthy()).isTrue();

        redis.stop();

        assertThat(redis.isRunning()).isFalse();
        assertThat(stateStore.isHealthy()).isFalse();

        assertThatThrownBy(() -> stateStore.recordAndCountSlidingWindow(
                "fraudguard:outage:test",
                "event-1",
                1_000L,
                60_000L,
                120L
        )).isInstanceOf(QueryTimeoutException.class);
    }
}