package com.fraudguard.domain.rule;

import com.fraudguard.config.FraudGuardProperties;
import com.fraudguard.domain.model.RuleResult;
import com.fraudguard.domain.model.Transaction;
import com.fraudguard.domain.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HighAmountRuleTest {

    private static final long THRESHOLD = 5_000_000L; // 50,000 INR
    private static final int WEIGHT = 50;
    private static final String REASON_CODE = "HIGH_AMOUNT";
    private static final UUID EVENT_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID SRC_WALLET = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DST_WALLET = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private FraudGuardProperties properties;
    private HighAmountRule rule;

    @BeforeEach
    void setUp() {
        properties = new FraudGuardProperties(
                new FraudGuardProperties.ThresholdProperties(40, 70),
                new FraudGuardProperties.RulesProperties(
                        new FraudGuardProperties.HighAmountRuleProperties(true, THRESHOLD, WEIGHT, REASON_CODE),
                        null
                ),
                null
        );
        rule = new HighAmountRule(properties);
    }

    private Transaction createTransaction(long amountPaise) {
        return new Transaction(
                EVENT_ID,
                SRC_WALLET,
                DST_WALLET,
                amountPaise,
                TransactionType.TRANSFER,
                "INR",
                Instant.parse("2026-11-09T10:00:00Z")
        );
    }

    @Test
    @DisplayName("Boundary: amount equal to threshold does not trigger rule")
    void shouldNotTriggerWhenAmountEqualsThreshold() {
        RuleResult result = rule.evaluate(createTransaction(THRESHOLD));

        assertThat(result.triggered()).isFalse();
        assertThat(result.score()).isZero();
        assertThat(result.reasonCode()).isNull();
    }

    @Test
    @DisplayName("Boundary: amount 1 paise below threshold does not trigger rule")
    void shouldNotTriggerWhenAmountIsBelowThreshold() {
        RuleResult result = rule.evaluate(createTransaction(THRESHOLD - 1));

        assertThat(result.triggered()).isFalse();
        assertThat(result.score()).isZero();
    }

    @Test
    @DisplayName("Boundary: amount 1 paise above threshold triggers rule with configured weight and reason code")
    void shouldTriggerWhenAmountIsAboveThreshold() {
        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1));

        assertThat(result.triggered()).isTrue();
        assertThat(result.score()).isEqualTo(WEIGHT);
        assertThat(result.reasonCode()).isEqualTo(REASON_CODE);
        assertThat(result.ruleName()).isEqualTo(HighAmountRule.RULE_NAME);
    }

    @Test
    @DisplayName("Should not trigger when rule is disabled in configuration")
    void shouldNotTriggerWhenRuleIsDisabled() {
        FraudGuardProperties disabledProps = new FraudGuardProperties(
                new FraudGuardProperties.ThresholdProperties(40, 70),
                new FraudGuardProperties.RulesProperties(
                        new FraudGuardProperties.HighAmountRuleProperties(false, THRESHOLD, WEIGHT, REASON_CODE),
                        null
                ),
                null
        );
        HighAmountRule disabledRule = new HighAmountRule(disabledProps);

        RuleResult result = disabledRule.evaluate(createTransaction(THRESHOLD + 100_000L));

        assertThat(result.triggered()).isFalse();
    }

    @Test
    @DisplayName("Should safely handle null transaction")
    void shouldHandleNullTransactionSafely() {
        RuleResult result = rule.evaluate(null);

        assertThat(result.triggered()).isFalse();
        assertThat(result.score()).isZero();
    }
}
