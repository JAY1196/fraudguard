package com.fraudguard.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudguard.api.dto.EvaluateRiskRequest;
import com.fraudguard.domain.model.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {"fraudguard.rules.high-amount.weight=80", "fraudguard.rules.unusual-hours.enabled=false"})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RiskEvaluationRedisDegradedModeIntegrationTest {

    private static final UUID SOURCE_WALLET =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DESTINATION_WALLET =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.timeout", () -> "500ms");
    }
    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @Order(1)
    @DisplayName("Healthy Redis returns 422 when an event ID is replayed with a changed amount")
    void shouldReturnPayloadMismatchWhenRedisIsHealthy() throws Exception {
        UUID eventId = UUID.randomUUID();
        Instant timestamp = Instant.now();

        EvaluateRiskRequest originalRequest = createRequest(eventId, 100_000L, timestamp);
        EvaluateRiskRequest conflictingRequest = createRequest(eventId, 200_000L, timestamp);

        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(originalRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded", is(false)));

        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictingRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status", is(422)))
                .andExpect(jsonPath("$.code", is("EVENT_PAYLOAD_MISMATCH")));
    }

    @Test
    @Order(2)
    @DisplayName("Stopped dedicated Redis enables degraded mode while the stateless high-amount rule blocks")
    void shouldBlockHighAmountTransactionWhenRedisIsUnavailable() throws Exception {
        redis.stop();

        org.assertj.core.api.Assertions.assertThat(redis.isRunning()).isFalse();

        EvaluateRiskRequest request = createRequest(
                UUID.randomUUID(),
                10_000_000L,
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("BLOCK")))
                .andExpect(jsonPath("$.score", is(80)))
                .andExpect(jsonPath("$.degraded", is(true)))
                .andExpect(jsonPath("$.reason_codes", hasItem("DEGRADED")))
                .andExpect(jsonPath("$.reason_codes", hasItem("HIGH_AMOUNT")));
    }

    private EvaluateRiskRequest createRequest(UUID eventId, long amountPaise, Instant timestamp) {
        return new EvaluateRiskRequest(
                eventId,
                SOURCE_WALLET,
                DESTINATION_WALLET,
                amountPaise,
                TransactionType.TRANSFER,
                "INR",
                timestamp
        );
    }
}