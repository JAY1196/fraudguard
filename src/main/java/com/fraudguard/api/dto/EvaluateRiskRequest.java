package com.fraudguard.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fraudguard.domain.model.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;
import java.util.Locale;

@Schema(description = "Transaction payload for fraud risk evaluation")
public record EvaluateRiskRequest(
        @NotNull(message = "event_id is required")
        @Schema(description = "Unique event identifier (UUID)", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        @JsonProperty("event_id")
        UUID eventId,

        @NotNull(message = "source_wallet_id is required")
        @Schema(description = "Identifier of the source wallet (UUID)", example = "11111111-1111-1111-1111-111111111111")
        @JsonProperty("source_wallet_id")
        UUID sourceWalletId,

        @NotNull(message = "destination_wallet_id is required")
        @Schema(description = "Identifier of the destination wallet (UUID)", example = "22222222-2222-2222-2222-222222222222")
        @JsonProperty("destination_wallet_id")
        UUID destinationWalletId,

        @NotNull(message = "amount_paise is required")
        @Min(value = 1, message = "amount_paise must be greater than 0")
        @Schema(description = "Transaction amount in paise (1 INR = 100 paise)", example = "2500000")
        @JsonProperty("amount_paise")
        Long amountPaise,

        @NotNull(message = "type is required")
        @Schema(description = "Type of transaction", example = "TRANSFER",
                allowableValues = {"TOPUP", "TRANSFER", "WITHDRAWAL", "REVERSAL"})
        TransactionType type,

        @Schema(description = "ISO-4217 currency code (defaults to INR)", example = "INR")
        String currency,

        @NotNull(message = "timestamp is required")
        @Schema(description = "ISO-8601 UTC timestamp of the event", example = "2026-11-09T10:30:00Z")
        Instant timestamp
) {
    public EvaluateRiskRequest {
        if (currency == null || currency.isBlank()) {
            currency = "INR";
        } else {
            currency = currency.trim().toUpperCase(Locale.ROOT);
        }
    }
}
