package com.fraudguard.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudguard.PostgresTestcontainersConfiguration;
import com.fraudguard.TestcontainersConfiguration;
import com.fraudguard.api.dto.EvaluateRiskRequest;
import com.fraudguard.domain.engine.RuleEngine;
import com.fraudguard.domain.model.Transaction;
import com.fraudguard.domain.model.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@Import({
        TestcontainersConfiguration.class,
        PostgresTestcontainersConfiguration.class
})
class RiskEvaluationIdempotencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private RuleEngine ruleEngine;

    @Test
    void shouldReturnStoredDecisionWithoutReevaluatingExactRetry() throws Exception {
        EvaluateRiskRequest request = createRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100_000L
        );

        String requestBody = objectMapper.writeValueAsString(request);
        clearInvocations(ruleEngine);

        MvcResult firstResult = mockMvc.perform(post("/api/v1/risk/evaluate")
                        .header("X-API-Key", "test-only-fraudguard-api-key-2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();

        assertThat(firstResult.getResponse().getStatus()).isEqualTo(200);

        MvcResult retryResult = mockMvc.perform(post("/api/v1/risk/evaluate")
                        .header("X-API-Key", "test-only-fraudguard-api-key-2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();

        assertThat(retryResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(retryResult.getResponse().getContentAsString())
                .isEqualTo(firstResult.getResponse().getContentAsString());
        assertThat(countDecisions(request.eventId())).isEqualTo(1);

        verify(ruleEngine, times(1)).evaluate(any(Transaction.class));
    }

    @Test
    void shouldRejectChangedPayloadWithoutCreatingAnotherDecision() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID sourceWalletId = UUID.randomUUID();
        UUID destinationWalletId = UUID.randomUUID();

        EvaluateRiskRequest originalRequest = createRequest(
                eventId, sourceWalletId, destinationWalletId, 100_000L
        );

        EvaluateRiskRequest changedRequest = createRequest(
                eventId, sourceWalletId, destinationWalletId, 200_000L
        );

        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .header("X-API-Key", "test-only-fraudguard-api-key-2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(originalRequest)))
                .andReturn();

        MvcResult mismatchResult = mockMvc.perform(post("/api/v1/risk/evaluate")
                        .header("X-API-Key", "test-only-fraudguard-api-key-2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changedRequest)))
                .andReturn();

        assertThat(mismatchResult.getResponse().getStatus()).isEqualTo(422);
        assertThat(mismatchResult.getResponse().getContentAsString())
                .contains("EVENT_PAYLOAD_MISMATCH");
        assertThat(countDecisions(eventId)).isEqualTo(1);
    }

    @Test
    void shouldPersistOnlyOneDecisionForConcurrentDuplicateRequests() throws Exception {
        EvaluateRiskRequest request = createRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100_000L
        );

        String requestBody = objectMapper.writeValueAsString(request);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        clearInvocations(ruleEngine);

        try {
            Future<MvcResult> firstRequest = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start first request");
                }
                return mockMvc.perform(post("/api/v1/risk/evaluate")
                                .header("X-API-Key", "test-only-fraudguard-api-key-2026")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                        .andReturn();
            });

            Future<MvcResult> secondRequest = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start second request");
                }
                return mockMvc.perform(post("/api/v1/risk/evaluate")
                                .header("X-API-Key", "test-only-fraudguard-api-key-2026")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                        .andReturn();
            });

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            MvcResult firstResult = firstRequest.get(30, TimeUnit.SECONDS);
            MvcResult secondResult = secondRequest.get(30, TimeUnit.SECONDS);

            assertThat(firstResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(secondResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(firstResult.getResponse().getContentAsString())
                    .isEqualTo(secondResult.getResponse().getContentAsString());
            assertThat(countDecisions(request.eventId())).isEqualTo(1);

            verify(ruleEngine, times(1)).evaluate(any(Transaction.class));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shouldRollbackClaimWhenRiskEvaluationFailsAndAllowRetry() throws Exception {
        EvaluateRiskRequest request = createRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100_000L
        );

        String requestBody = objectMapper.writeValueAsString(request);

        doThrow(new IllegalStateException("Simulated rule engine failure"))
                .when(ruleEngine)
                .evaluate(any(Transaction.class));

        MvcResult failedResult;

        try {
            failedResult = mockMvc.perform(post("/api/v1/risk/evaluate")
                            .header("X-API-Key", "test-only-fraudguard-api-key-2026")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andReturn();
        } finally {
            reset(ruleEngine);
        }

        assertThat(failedResult.getResponse().getStatus()).isEqualTo(500);
        assertThat(countDecisions(request.eventId())).isZero();

        MvcResult retryResult = mockMvc.perform(post("/api/v1/risk/evaluate")
                        .header("X-API-Key", "test-only-fraudguard-api-key-2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();

        assertThat(retryResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(countDecisions(request.eventId())).isEqualTo(1);
    }

    private long countDecisions(UUID eventId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM risk_decisions WHERE event_id = ?",
                Long.class,
                eventId
        );
        return count == null ? 0 : count;
    }

    private EvaluateRiskRequest createRequest(
            UUID eventId,
            UUID sourceWalletId,
            UUID destinationWalletId,
            long amountPaise
    ) {
        return new EvaluateRiskRequest(
                eventId,
                sourceWalletId,
                destinationWalletId,
                amountPaise,
                TransactionType.TRANSFER,
                "INR",
                Instant.now()
        );
    }
}
