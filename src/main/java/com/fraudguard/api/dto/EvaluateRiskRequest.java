package com.fraudguard.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@Schema(description = "Transaction payload for fraud risk evaluation")
public record EvaluateRiskRequest(
        @NotBlank(message = "eventId is required")
        @Schema(description = "Unique event identifier for the transaction", example = "evt_9a8b7c6d")
        String eventId,

        @NotBlank(message = "sourceWalletId is required")
        @Schema(description = "Identifier of the source wallet", example = "wlt_src_1234")
        String sourceWalletId,

        @NotBlank(message = "destinationWalletId is required")
        @Schema(description = "Identifier of the destination wallet", example = "wlt_dst_5678")
        String destinationWalletId,

        @NotNull(message = "amountPaise is required")
        @Min(value = 1, message = "amountPaise must be greater than 0")
        @Schema(description = "Transaction amount in paise (1 INR = 100 paise)", example = "2500000")
        Long amountPaise,

        @NotBlank(message = "type is required")
        @Schema(description = "Type of transaction", example = "P2P_TRANSFER")
        String type,

        @NotNull(message = "timestamp is required")
        @Schema(description = "ISO-8601 UTC timestamp of the event", example = "2026-11-09T10:30:00Z")
        Instant timestamp
) {}
