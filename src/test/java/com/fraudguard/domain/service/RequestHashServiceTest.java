package com.fraudguard.domain.service;

import com.fraudguard.api.dto.EvaluateRiskRequest;
import com.fraudguard.domain.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestHashServiceTest {

    private RequestHashService hashService;
    private EvaluateRiskRequest baseline;

    @BeforeEach
    void setUp() {
        hashService = new RequestHashService();
        baseline = request(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                100_000L,
                TransactionType.TRANSFER,
                "INR",
                Instant.parse("2026-11-13T10:00:00Z")
        );
    }

    @Test
    void shouldProduceSameHashForIdenticalRequests() {
        assertEquals(hashService.hash(baseline), hashService.hash(baseline));
    }

    @Test
    void shouldNormalizeCurrencyBeforeHashing() {
        EvaluateRiskRequest normalized = request(
                baseline.eventId(),
                baseline.sourceWalletId(),
                baseline.destinationWalletId(),
                baseline.amountPaise(),
                baseline.type(),
                " inr ",
                baseline.timestamp()
        );

        assertEquals(hashService.hash(baseline), hashService.hash(normalized));
    }

    @Test
    void shouldDefaultBlankCurrencyToInr() {
        EvaluateRiskRequest blankCurrency = request(
                baseline.eventId(),
                baseline.sourceWalletId(),
                baseline.destinationWalletId(),
                baseline.amountPaise(),
                baseline.type(),
                "   ",
                baseline.timestamp()
        );

        assertEquals(hashService.hash(baseline), hashService.hash(blankCurrency));
    }

    @Test
    void shouldProduceSha256HexHash() {
        String hash = hashService.hash(baseline);

        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void shouldChangeHashWhenAnyBusinessFieldChanges() {
        assertDifferent(baseline.eventId(), baseline.sourceWalletId(),
                baseline.destinationWalletId(), baseline.amountPaise(),
                baseline.type(), baseline.currency(), baseline.timestamp().plusSeconds(1));

        assertDifferent(UUID.randomUUID(), baseline.sourceWalletId(),
                baseline.destinationWalletId(), baseline.amountPaise(),
                baseline.type(), baseline.currency(), baseline.timestamp());

        assertDifferent(baseline.eventId(), UUID.randomUUID(),
                baseline.destinationWalletId(), baseline.amountPaise(),
                baseline.type(), baseline.currency(), baseline.timestamp());

        assertDifferent(baseline.eventId(), baseline.sourceWalletId(),
                UUID.randomUUID(), baseline.amountPaise(),
                baseline.type(), baseline.currency(), baseline.timestamp());

        assertDifferent(baseline.eventId(), baseline.sourceWalletId(),
                baseline.destinationWalletId(), baseline.amountPaise() + 1,
                baseline.type(), baseline.currency(), baseline.timestamp());

        assertDifferent(baseline.eventId(), baseline.sourceWalletId(),
                baseline.destinationWalletId(), baseline.amountPaise(),
                TransactionType.TOPUP, baseline.currency(), baseline.timestamp());

        assertDifferent(baseline.eventId(), baseline.sourceWalletId(),
                baseline.destinationWalletId(), baseline.amountPaise(),
                baseline.type(), "USD", baseline.timestamp());
    }

    private void assertDifferent(
            UUID eventId,
            UUID sourceWalletId,
            UUID destinationWalletId,
            Long amountPaise,
            TransactionType type,
            String currency,
            Instant timestamp
    ) {
        EvaluateRiskRequest changed = request(
                eventId, sourceWalletId, destinationWalletId,
                amountPaise, type, currency, timestamp
        );

        assertNotEquals(hashService.hash(baseline), hashService.hash(changed));
    }

    private EvaluateRiskRequest request(
            UUID eventId,
            UUID sourceWalletId,
            UUID destinationWalletId,
            Long amountPaise,
            TransactionType type,
            String currency,
            Instant timestamp
    ) {
        return new EvaluateRiskRequest(
                eventId, sourceWalletId, destinationWalletId,
                amountPaise, type, currency, timestamp
        );
    }
}