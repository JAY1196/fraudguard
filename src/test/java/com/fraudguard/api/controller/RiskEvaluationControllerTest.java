package com.fraudguard.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudguard.api.dto.EvaluateRiskRequest;
import com.fraudguard.config.ApiKeySecurityConfiguration;
import com.fraudguard.api.dto.EvaluateRiskResponse;
import com.fraudguard.api.service.RiskEvaluationService;
import com.fraudguard.domain.model.RiskDecision;
import com.fraudguard.exception.GlobalExceptionHandler;
import com.fraudguard.exception.TimestampOutOfRangeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import org.mockito.ArgumentCaptor;

@WebMvcTest(RiskEvaluationController.class)
@TestPropertySource(properties = "fraudguard.security.api-key=test-only-fraudguard-api-key-2026")
@Import({GlobalExceptionHandler.class, ApiKeySecurityConfiguration.class})
class RiskEvaluationControllerTest {

    private static final String TEST_API_KEY = "test-only-fraudguard-api-key-2026";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private RiskEvaluationService riskEvaluationService;

    private static final UUID EVENT_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID SRC_WALLET = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DST_WALLET = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private EvaluateRiskRequest createValidRequest() {
        return new EvaluateRiskRequest(
                EVENT_ID,
                SRC_WALLET,
                DST_WALLET,
                500000L,
                com.fraudguard.domain.model.TransactionType.TRANSFER,
                null,
                Instant.parse("2026-11-09T10:00:00Z")
        );
    }

    private EvaluateRiskResponse response(
            RiskDecision decision,
            int score,
            List<String> reasonCodes
    ) {
        return new EvaluateRiskResponse(
                EVENT_ID,
                decision,
                score,
                reasonCodes,
                false
        );
    }

    @Test
    @DisplayName("POST /api/v1/risk/evaluate happy path: returns ALLOW decision with score 0 and snake_case JSON")
    void shouldReturnAllowDecision() throws Exception {
        when(riskEvaluationService.evaluate(any(EvaluateRiskRequest.class)))
                .thenReturn(response(RiskDecision.ALLOW, 0, List.of()));

        EvaluateRiskRequest request = createValidRequest();

        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .header("X-API-Key", TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_id", is(EVENT_ID.toString())))
                .andExpect(jsonPath("$.decision", is("ALLOW")))
                .andExpect(jsonPath("$.score", is(0)))
                .andExpect(jsonPath("$.reason_codes").isArray());

        verify(riskEvaluationService).evaluate(any(EvaluateRiskRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/risk/evaluate happy path: returns BLOCK decision with reasons")
    void shouldReturnBlockDecision() throws Exception {
        when(riskEvaluationService.evaluate(any(EvaluateRiskRequest.class)))
                .thenReturn(response(
                        RiskDecision.BLOCK,
                        90,
                        List.of("HIGH_AMOUNT", "UNUSUAL_HOURS_HIGH_AMOUNT")
                ));

        EvaluateRiskRequest request = createValidRequest();

        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .header("X-API-Key", TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_id", is(EVENT_ID.toString())))
                .andExpect(jsonPath("$.decision", is("BLOCK")))
                .andExpect(jsonPath("$.score", is(90)))
                .andExpect(jsonPath("$.reason_codes", hasItem("HIGH_AMOUNT")))
                .andExpect(jsonPath("$.reason_codes", hasItem("UNUSUAL_HOURS_HIGH_AMOUNT")));
    }

    @Test
    @DisplayName("POST /api/v1/risk/evaluate validation failure: null event_id returns 400 VALIDATION_ERROR")
    void shouldReturnProblemDetailOnMissingEventId() throws Exception {
        String json = """
                {
                  "source_wallet_id": "11111111-1111-1111-1111-111111111111",
                  "destination_wallet_id": "22222222-2222-2222-2222-222222222222",
                  "amount_paise": 500000,
                  "type": "TRANSFER",
                  "timestamp": "2026-11-09T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .header("X-API-Key", TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Bad Request")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.errors.eventId").exists());
    }

    @Test
    @DisplayName("POST /api/v1/risk/evaluate validation failure: non-positive amount returns 400 VALIDATION_ERROR")
    void shouldReturnProblemDetailOnNonPositiveAmount() throws Exception {
        String json = """
                {
                  "event_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                  "source_wallet_id": "11111111-1111-1111-1111-111111111111",
                  "destination_wallet_id": "22222222-2222-2222-2222-222222222222",
                  "amount_paise": 0,
                  "type": "TRANSFER",
                  "timestamp": "2026-11-09T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .header("X-API-Key", TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.errors.amountPaise").exists());
    }

    @Test
    @DisplayName("POST /api/v1/risk/evaluate timestamp out of range returns 400 TIMESTAMP_OUT_OF_RANGE")
    void shouldReturnProblemDetailOnTimestampOutOfRange() throws Exception {
        doThrow(new TimestampOutOfRangeException(
                "Transaction timestamp is too far in the future: 2026-11-09T12:00:00Z"
        )).when(riskEvaluationService).evaluate(any(EvaluateRiskRequest.class));

        EvaluateRiskRequest request = createValidRequest();

        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .header("X-API-Key", TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Timestamp Out Of Range")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("TIMESTAMP_OUT_OF_RANGE")))
                .andExpect(jsonPath("$.detail", containsString("too far in the future")));
    }

    @Test
    @DisplayName("POST /api/v1/risk/evaluate malformed body returns 400 VALIDATION_ERROR")
    void shouldReturnProblemDetailOnMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .header("X-API-Key", TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("POST /api/v1/risk/evaluate invalid UUID returns 400 VALIDATION_ERROR")
    void shouldReturnProblemDetailOnInvalidUUID() throws Exception {
        String json = """
                {
                  "event_id": "not-a-uuid",
                  "source_wallet_id": "11111111-1111-1111-1111-111111111111",
                  "destination_wallet_id": "22222222-2222-2222-2222-222222222222",
                  "amount_paise": 500000,
                  "type": "TRANSFER",
                  "timestamp": "2026-11-09T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .header("X-API-Key", TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.detail", containsString("Invalid UUID")));
    }

    @Test
    @DisplayName("POST /api/v1/risk/evaluate unknown transaction type returns 400 VALIDATION_ERROR")
    void shouldReturnProblemDetailOnUnknownType() throws Exception {
        String json = """
                {
                  "event_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                  "source_wallet_id": "11111111-1111-1111-1111-111111111111",
                  "destination_wallet_id": "22222222-2222-2222-2222-222222222222",
                  "amount_paise": 500000,
                  "type": "P2P_TRANSFER",
                  "timestamp": "2026-11-09T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .header("X-API-Key", TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.detail", containsString(
                        "Invalid value for field 'type'"
                )));
    }

    @Test
    @DisplayName("POST /api/v1/risk/evaluate currency defaults to INR when omitted")
    void shouldDefaultCurrencyToINR() throws Exception {
        when(riskEvaluationService.evaluate(any(EvaluateRiskRequest.class)))
                .thenReturn(response(RiskDecision.ALLOW, 0, List.of()));

        String json = """
                {
                  "event_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                  "source_wallet_id": "11111111-1111-1111-1111-111111111111",
                  "destination_wallet_id": "22222222-2222-2222-2222-222222222222",
                  "amount_paise": 500000,
                  "type": "TRANSFER",
                  "timestamp": "2026-11-09T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .header("X-API-Key", TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("ALLOW")));

        ArgumentCaptor<EvaluateRiskRequest> captor =
                ArgumentCaptor.forClass(EvaluateRiskRequest.class);

        verify(riskEvaluationService).evaluate(captor.capture());
        assertEquals("INR", captor.getValue().currency());
    }

    @Test
    @DisplayName("Missing API key returns 401 UNAUTHENTICATED ProblemDetail")
    void shouldRejectRequestWithoutApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createValidRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", is("urn:problem:unauthenticated")))
                .andExpect(jsonPath("$.title", is("Unauthorized")))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.detail", is("A valid API key is required")))
                .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Invalid API key returns 401 UNAUTHENTICATED ProblemDetail")
    void shouldRejectRequestWithInvalidApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .header("X-API-Key", "incorrect-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createValidRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", is("urn:problem:unauthenticated")))
                .andExpect(jsonPath("$.title", is("Unauthorized")))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.detail", is("A valid API key is required")))
                .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}