package com.fraudguard.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Transaction(
        UUID eventId,
        UUID sourceWalletId,
        UUID destinationWalletId,
        long amountPaise,
        TransactionType type,
        String currency,
        Instant timestamp
) {}
