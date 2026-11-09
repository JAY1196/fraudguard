package com.fraudguard.api.dto;

import com.fraudguard.domain.model.RiskDecision;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Fraud risk evaluation response")
public record EvaluateRiskResponse(
        @Schema(description = "Transaction event identifier", example = "evt_9a8b7c6d")
        String eventId,

        @Schema(description = "Determined risk decision: ALLOW, REVIEW, or BLOCK", example = "ALLOW")
        RiskDecision decision,

        @Schema(description = "Risk score capped between 0 and 100", example = "0")
        int score,

        @Schema(description = "Sorted list of triggered fraud reason codes", example = "[\"HIGH_AMOUNT\"]")
        List<String> reasonCodes
) {}
