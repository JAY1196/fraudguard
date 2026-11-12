package com.fraudguard.domain.rule;

import com.fraudguard.config.FraudGuardProperties;
import com.fraudguard.domain.model.RuleResult;
import com.fraudguard.domain.model.Transaction;
import com.fraudguard.domain.model.TransactionType;
import com.fraudguard.domain.store.StateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AmountSpikeRuleTest {

    private static final String REDIS_KEY_PREFIX = "fraudguard:amount-spike:{";
    private static final UUID EVENT_ID =
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID SRC_WALLET =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DST_WALLET =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SYSTEM_WALLET =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant EVENT_TIME =
            Instant.parse("2026-11-09T10:00:00Z");

    private static final double MULTIPLIER = 3.0;
    private static final int HISTORY_SIZE = 5;
    private static final int MINIMUM_HISTORY = 3;
    private static final int WEIGHT = 35;
    private static final String REASON_CODE = "AMOUNT_SPIKE";
    private static final int MAX_HISTORY = 100;
    private static final long TTL_SECONDS = 86400;

    @Mock
    private StateStore stateStore;

    private AmountSpikeRule rule;

    @BeforeEach
    void setUp() {
        rule = new AmountSpikeRule(buildProperties(true), stateStore);
    }

    private FraudGuardProperties buildProperties(boolean enabled) {
        return new FraudGuardProperties(
                new FraudGuardProperties.ThresholdProperties(40, 70),
                new FraudGuardProperties.RulesProperties(
                        null,
                        null,
                        null,
                        new FraudGuardProperties.AmountSpikeRuleProperties(
                                enabled,
                                MULTIPLIER,
                                HISTORY_SIZE,
                                MINIMUM_HISTORY,
                                WEIGHT,
                                REASON_CODE,
                                MAX_HISTORY,
                                TTL_SECONDS
                        )
                ),
                null
        );
    }

    private Transaction createTransaction(
            TransactionType type,
            UUID source,
            UUID destination,
            long amountPaise) {
        return new Transaction(
                EVENT_ID,
                source,
                destination,
                amountPaise,
                type,
                "INR",
                EVENT_TIME
        );
    }

    private void stubBaseline(Long... amounts) {
        when(stateStore.recordAndGetPriorAmounts(
                anyString(), anyString(), anyLong(), anyLong(),
                anyInt(), anyInt(), anyLong()
        )).thenReturn(List.of(amounts));
    }

    private void verifyWalletRecorded(UUID walletId) {
        verify(stateStore).recordAndGetPriorAmounts(
                eq(REDIS_KEY_PREFIX + walletId + "}"),
                eq(EVENT_ID.toString()),
                anyLong(),
                eq(EVENT_TIME.toEpochMilli()),
                eq(HISTORY_SIZE),
                eq(MAX_HISTORY),
                eq(TTL_SECONDS)
        );
    }

    @Test
    @DisplayName("Should not trigger when history is below minimum")
    void shouldNotTriggerWithInsufficientHistory() {
        stubBaseline(10_000L, 10_000L);

        RuleResult result = rule.evaluate(
                createTransaction(TransactionType.TRANSFER, SRC_WALLET, DST_WALLET, 100_000L)
        );

        assertThat(result.triggered()).isFalse();
        assertThat(result.score()).isZero();
        verifyWalletRecorded(SRC_WALLET);
    }

    @Test
    @DisplayName("Should not trigger when amount equals three times the average")
    void shouldNotTriggerAtExactThreshold() {
        stubBaseline(10_000L, 10_000L, 10_000L);

        RuleResult result = rule.evaluate(
                createTransaction(TransactionType.TRANSFER, SRC_WALLET, DST_WALLET, 30_000L)
        );

        assertThat(result.triggered()).isFalse();
        verifyWalletRecorded(SRC_WALLET);
    }

    @Test
    @DisplayName("Should trigger when amount exceeds three times the average")
    void shouldTriggerAboveThreshold() {
        stubBaseline(10_000L, 10_000L, 10_000L);

        RuleResult result = rule.evaluate(
                createTransaction(TransactionType.TRANSFER, SRC_WALLET, DST_WALLET, 30_001L)
        );

        assertThat(result.triggered()).isTrue();
        assertThat(result.score()).isEqualTo(WEIGHT);
        assertThat(result.reasonCode()).isEqualTo(REASON_CODE);
        assertThat(result.ruleName()).isEqualTo(AmountSpikeRule.RULE_NAME);
        verifyWalletRecorded(SRC_WALLET);
    }

    @Test
    @DisplayName("TRANSFER should use source wallet as actor")
    void shouldUseSourceWalletForTransfer() {
        stubBaseline(10_000L, 10_000L, 10_000L);

        rule.evaluate(createTransaction(
                TransactionType.TRANSFER, SRC_WALLET, DST_WALLET, 10_000L
        ));

        verifyWalletRecorded(SRC_WALLET);
    }

    @Test
    @DisplayName("WITHDRAWAL should use source wallet as actor")
    void shouldUseSourceWalletForWithdrawal() {
        stubBaseline(10_000L, 10_000L, 10_000L);

        rule.evaluate(createTransaction(
                TransactionType.WITHDRAWAL, SRC_WALLET, DST_WALLET, 10_000L
        ));

        verifyWalletRecorded(SRC_WALLET);
    }

    @Test
    @DisplayName("TOPUP should use destination wallet as actor")
    void shouldUseDestinationWalletForTopup() {
        stubBaseline(10_000L, 10_000L, 10_000L);

        rule.evaluate(createTransaction(
                TransactionType.TOPUP, SRC_WALLET, DST_WALLET, 10_000L
        ));

        verifyWalletRecorded(DST_WALLET);
    }

    @Test
    @DisplayName("REVERSAL should be skipped")
    void shouldSkipReversal() {
        RuleResult result = rule.evaluate(
                createTransaction(TransactionType.REVERSAL, SRC_WALLET, DST_WALLET, 10_000L)
        );

        assertThat(result.triggered()).isFalse();
        verifyNoInteractions(stateStore);
    }

    @Test
    @DisplayName("Should skip SYSTEM wallet")
    void shouldSkipSystemWallet() {
        RuleResult result = rule.evaluate(
                createTransaction(TransactionType.TRANSFER, SYSTEM_WALLET, DST_WALLET, 10_000L)
        );

        assertThat(result.triggered()).isFalse();
        verifyNoInteractions(stateStore);
    }

    @Test
    @DisplayName("Should not access state store when rule is disabled")
    void shouldNotEvaluateWhenDisabled() {
        AmountSpikeRule disabledRule = new AmountSpikeRule(buildProperties(false), stateStore);

        RuleResult result = disabledRule.evaluate(
                createTransaction(TransactionType.TRANSFER, SRC_WALLET, DST_WALLET, 100_000L)
        );

        assertThat(result.triggered()).isFalse();
        verifyNoInteractions(stateStore);
    }

    @Test
    @DisplayName("Should safely handle null transaction")
    void shouldHandleNullTransaction() {
        RuleResult result = rule.evaluate(null);

        assertThat(result.triggered()).isFalse();
        verifyNoInteractions(stateStore);
    }
}