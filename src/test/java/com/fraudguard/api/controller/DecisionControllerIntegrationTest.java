package com.fraudguard.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudguard.PostgresTestcontainersConfiguration;
import com.fraudguard.TestcontainersConfiguration;
import com.fraudguard.api.dto.EvaluateRiskRequest;
import com.fraudguard.domain.model.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@Import({
        TestcontainersConfiguration.class,
        PostgresTestcontainersConfiguration.class
})
class DecisionControllerIntegrationTest {

    private static final String API_KEY = "test-only-fraudguard-api-key-2026";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldReturnSourceAndDestinationDecisionsInDescendingOrder()
            throws Exception {

        UUID walletId = UUID.randomUUID();

        createDecision(walletId, UUID.randomUUID());
        createDecision(UUID.randomUUID(), walletId);
        createDecision(UUID.randomUUID(), UUID.randomUUID());

        JsonNode body = getPage(walletId, 20, null);
        JsonNode decisions = body.get("decisions");

        assertThat(decisions).hasSize(2);

        assertThat(decisions.get(0).get("id").asLong())
                .isGreaterThan(decisions.get(1).get("id").asLong());

        for (JsonNode decision : decisions) {
            assertThat(
                    decision.get("source_wallet_id").asText().equals(walletId.toString())
                    || decision.get("destination_wallet_id").asText().equals(walletId.toString())
            ).isTrue();
        }

        assertThat(body.get("has_more").asBoolean()).isFalse();
        assertThat(body.get("next_cursor").isNull()).isTrue();
    }

    @Test
    void shouldReturnSelfTransferOnlyOnce() throws Exception {
        UUID walletId = UUID.randomUUID();

        createDecision(walletId, walletId);

        JsonNode body = getPage(walletId, 20, null);

        assertThat(body.get("decisions")).hasSize(1);
        assertThat(body.get("decisions").get(0).get("source_wallet_id").asText())
                .isEqualTo(walletId.toString());
        assertThat(body.get("decisions").get(0).get("destination_wallet_id").asText())
                .isEqualTo(walletId.toString());
    }

    @Test
    void shouldReturnNextPageWithoutDuplicateDecisions() throws Exception {
        UUID walletId = UUID.randomUUID();

        createDecision(walletId, UUID.randomUUID());
        createDecision(UUID.randomUUID(), walletId);
        createDecision(walletId, UUID.randomUUID());

        JsonNode firstPage = getPage(walletId, 2, null);

        assertThat(firstPage.get("decisions")).hasSize(2);
        assertThat(firstPage.get("has_more").asBoolean()).isTrue();

        String cursor = firstPage.get("next_cursor").asText();

        assertThat(cursor).isNotBlank();
        assertThat(cursor).doesNotMatch("[0-9]+");

        JsonNode secondPage = getPage(walletId, 2, cursor);

        assertThat(secondPage.get("decisions")).hasSize(1);
        assertThat(secondPage.get("has_more").asBoolean()).isFalse();
        assertThat(secondPage.get("next_cursor").isNull()).isTrue();

        Set<Long> ids = new HashSet<>();

        firstPage.get("decisions").forEach(
                decision -> ids.add(decision.get("id").asLong())
        );

        secondPage.get("decisions").forEach(
                decision -> ids.add(decision.get("id").asLong())
        );

        assertThat(ids).hasSize(3);
    }

    @Test
    void shouldRejectInvalidLimit() throws Exception {
        UUID walletId = UUID.randomUUID();

        var result = mockMvc.perform(
                        get("/api/v1/wallets/{wallet_id}/decisions", walletId)
                                .header("X-API-Key", API_KEY)
                                .param("limit", "101")
                )
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void shouldRejectInvalidCursor() throws Exception {
        UUID walletId = UUID.randomUUID();

        var result = mockMvc.perform(
                        get("/api/v1/wallets/{wallet_id}/decisions", walletId)
                                .header("X-API-Key", API_KEY)
                                .param("cursor", "not-valid%%%")
                )
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void shouldRejectRequestWithoutApiKey() throws Exception {
        UUID walletId = UUID.randomUUID();

        var result = mockMvc.perform(
                        get("/api/v1/wallets/{wallet_id}/decisions", walletId)
                )
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    private JsonNode getPage(UUID walletId, int limit, String cursor)
            throws Exception {

        var request = get(
                        "/api/v1/wallets/{wallet_id}/decisions",
                        walletId
                )
                .header("X-API-Key", API_KEY)
                .param("limit", String.valueOf(limit));

        if (cursor != null) {
            request.param("cursor", cursor);
        }

        var result = mockMvc.perform(request).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        return objectMapper.readTree(
                result.getResponse().getContentAsString()
        );
    }

    private void createDecision(UUID sourceWalletId, UUID destinationWalletId)
            throws Exception {

        EvaluateRiskRequest request = new EvaluateRiskRequest(
                UUID.randomUUID(),
                sourceWalletId,
                destinationWalletId,
                100_000L,
                TransactionType.TRANSFER,
                "INR",
                Instant.now()
        );

        var result = mockMvc.perform(
                        post("/api/v1/risk/evaluate")
                                .header("X-API-Key", API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }
}