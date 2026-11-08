package com.fraudguard.domain.model;

import java.time.Instant;

public record Transaction(
        String eventId,
        String sourceWalletId,
        String destinationWalletId,
        long amountPaise,
        String type,
        Instant timestamp
) {}
