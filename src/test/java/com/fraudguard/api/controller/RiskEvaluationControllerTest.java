package com.fraudguard.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudguard.api.dto.EvaluateRiskRequest;
import com.fraudguard.api.validator.TransactionTimestampValidator;
import com.fraudguard.domain.engine.RuleEngine;
import com.fraudguard.domain.model.EvaluationResult;
import com.fraudguard.domain.model.RiskDecision;
import com.fraudguard.domain.model.Transaction;
import com.fraudguard.exception.GlobalExceptionHandler;
import com.fraudguard.exception.TimestampOutOfRangeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RiskEvaluationController.class)
@Import(GlobalExceptionHandler.class)
class RiskEvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private RuleEngine ruleEngine;

    @MockitoBean
    private TransactionTimestampValidator timestampValidator;

    private EvaluateRiskRequest createValidRequest() {
        return new EvaluateRiskRequest(
                "evt-101",
                "wallet-src-123",
                "wallet-dst-456",
                500000L,
                "TRANSFER",
                Instant.parse("2026-11-09T10:00:00Z")
        );
    }

    @Test
    @DisplayName("POST /api/v1/risk/evaluate happy path: returns ALLOW decision with score 0")
    void shouldReturnAllowDecision() throws Exception {
        when(ruleEngine.evaluate(any(Transaction.class))).thenReturn(
                new EvaluationResult(RiskDecision.ALLOW, 0, List.of())
        );

        EvaluateRiskRequest request = createValidRequest();

        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId", is("evt-101")))
                .andExpect(jsonPath("$.decision", is("ALLOW")))
                .andExpect(jsonPath("$.score", is(0)))
                .andExpect(jsonPath("$.reasonCodes").isArray());

        verify(timestampValidator).validate(request.timestamp());
        verify(ruleEngine).evaluate(any(Transaction.class));
    }

    @Test
    @DisplayName("POST /api/v1/risk/evaluate happy path: returns BLOCK decision with reasons")
    void shouldReturnBlockDecision() throws Exception {
        when(ruleEngine.evaluate(any(Transaction.class))).thenReturn(
                new EvaluationResult(RiskDecision.BLOCK, 90, List.of("HIGH_AMOUNT", "UNUSUAL_HOURS_HIGH_AMOUNT"))
        );

        EvaluateRiskRequest request = createValidRequest();

        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId", is("evt-101")))
                .andExpect(jsonPath("$.decision", is("BLOCK")))
                .andExpect(jsonPath("$.score", is(90)))
                .andExpect(jsonPath("$.reasonCodes", hasItem("HIGH_AMOUNT")))
                .andExpect(jsonPath("$.reasonCodes", hasItem("UNUSUAL_HOURS_HIGH_AMOUNT")));
    }

    @Test
    @DisplayName("POST /api/v1/risk/evaluate validation failure: missing required eventId returns 400 ProblemDetail")
    void shouldReturnProblemDetailOnMissingEventId() throws Exception {
        EvaluateRiskRequest invalidRequest = new EvaluateRiskRequest(
                "",
                "wallet-src-123",
                "wallet-dst-456",
                500000L,
                "TRANSFER",
                Instant.parse("2026-11-09T10:00:00Z")
        );

        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Bad Request")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.errors.eventId").exists());
    }

    @Test
    @DisplayName("POST /api/v1/risk/evaluate validation failure: non-positive amount returns 400 ProblemDetail")
    void shouldReturnProblemDetailOnNonPositiveAmount() throws Exception {
        EvaluateRiskRequest invalidRequest = new EvaluateRiskRequest(
                "evt-101",
                "wallet-src-123",
                "wallet-dst-456",
                0L,
                "TRANSFER",
                Instant.parse("2026-11-09T10:00:00Z")
        );

        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.errors.amountPaise").exists());
    }

    @Test
    @DisplayName("POST /api/v1/risk/evaluate timestamp out of range returns 400 ProblemDetail with TIMESTAMP_OUT_OF_RANGE")
    void shouldReturnProblemDetailOnTimestampOutOfRange() throws Exception {
        doThrow(new TimestampOutOfRangeException("Transaction timestamp is too far in the future: 2026-11-09T12:00:00Z"))
                .when(timestampValidator).validate(any(Instant.class));

        EvaluateRiskRequest request = createValidRequest();

        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Timestamp Out Of Range")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("TIMESTAMP_OUT_OF_RANGE")))
                .andExpect(jsonPath("$.detail", containsString("too far in the future")));
    }

    @Test
    @DisplayName("POST /api/v1/risk/evaluate malformed body returns 400 ProblemDetail with MALFORMED_JSON")
    void shouldReturnProblemDetailOnMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/risk/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("MALFORMED_JSON")));
    }
}
