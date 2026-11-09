package com.fraudguard.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fraudguard.domain.model.RiskDecision;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Fraud risk evaluation response")
public record EvaluateRiskResponse(
        @Schema(description = "Transaction event identifier", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        @JsonProperty("event_id")
        UUID eventId,

        @Schema(description = "Determined risk decision: ALLOW, REVIEW, or BLOCK", example = "ALLOW")
        RiskDecision decision,

        @Schema(description = "Risk score capped between 0 and 100", example = "0")
        int score,

        @Schema(description = "Sorted list of triggered fraud reason codes", example = "[\"HIGH_AMOUNT\"]")
        @JsonProperty("reason_codes")
        List<String> reasonCodes
) {}
