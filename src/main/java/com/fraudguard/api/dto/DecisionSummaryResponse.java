package com.fraudguard.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fraudguard.domain.model.RiskDecision;
import com.fraudguard.domain.model.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Stored fraud risk decision")
public record DecisionSummaryResponse(
        @JsonProperty("id")
        Long id,

        @JsonProperty("event_id")
        UUID eventId,

        @JsonProperty("source_wallet_id")
        UUID sourceWalletId,

        @JsonProperty("destination_wallet_id")
        UUID destinationWalletId,

        @JsonProperty("amount_paise")
        long amountPaise,

        @JsonProperty("transaction_type")
        TransactionType transactionType,

        String currency,

        @JsonProperty("event_timestamp")
        Instant eventTimestamp,

        RiskDecision decision,

        int score,

        @JsonProperty("reason_codes")
        List<String> reasonCodes,

        boolean degraded,

        @JsonProperty("evaluated_at")
        Instant evaluatedAt
) {
    public DecisionSummaryResponse {
        reasonCodes = List.copyOf(reasonCodes);
    }
}
