package com.fraudguard.domain.rule;

import com.fraudguard.config.FraudGuardProperties;
import com.fraudguard.domain.model.RuleResult;
import com.fraudguard.domain.model.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class UnusualHoursHighAmountRuleTest {

    private static final long THRESHOLD = 2_000_000L; // 20,000 INR
    private static final int WEIGHT = 40;
    private static final String REASON_CODE = "UNUSUAL_HOURS_HIGH_AMOUNT";
    private static final int START_HOUR = 23;
    private static final int END_HOUR = 5;

    private FraudGuardProperties buildProperties(boolean enabled, int start, int end) {
        return new FraudGuardProperties(
                new FraudGuardProperties.ThresholdProperties(40, 70),
                new FraudGuardProperties.RulesProperties(
                        null,
                        new FraudGuardProperties.UnusualHoursRuleProperties(
                                enabled, start, end, THRESHOLD, WEIGHT, REASON_CODE, "UTC"
                        )
                )
        );
    }

    private UnusualHoursHighAmountRule createRule(String instantUtc, boolean enabled, int start, int end) {
        Clock fixedClock = Clock.fixed(Instant.parse(instantUtc), ZoneOffset.UTC);
        return new UnusualHoursHighAmountRule(buildProperties(enabled, start, end), fixedClock);
    }

    private Transaction createTransaction(long amountPaise) {
        return new Transaction(
                "evt-test",
                "wallet-src",
                "wallet-dst",
                amountPaise,
                "TRANSFER",
                Instant.now()
        );
    }

    @Test
    @DisplayName("Boundary hour 22:59 (before window start 23:00) does not trigger even with high amount")
    void shouldNotTriggerBeforeStartHour() {
        UnusualHoursHighAmountRule rule = createRule("2026-11-09T22:59:59Z", true, START_HOUR, END_HOUR);
        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1));

        assertThat(result.triggered()).isFalse();
    }

    @Test
    @DisplayName("Boundary hour 23:00 (exact window start) triggers when amount exceeds threshold")
    void shouldTriggerAtExactStartHour() {
        UnusualHoursHighAmountRule rule = createRule("2026-11-09T23:00:00Z", true, START_HOUR, END_HOUR);
        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1));

        assertThat(result.triggered()).isTrue();
        assertThat(result.score()).isEqualTo(WEIGHT);
        assertThat(result.reasonCode()).isEqualTo(REASON_CODE);
        assertThat(result.ruleName()).isEqualTo(UnusualHoursHighAmountRule.RULE_NAME);
    }

    @Test
    @DisplayName("Boundary hour 04:59 (last minute of unusual window) triggers when amount exceeds threshold")
    void shouldTriggerAtEndOfOvernightWindow() {
        UnusualHoursHighAmountRule rule = createRule("2026-11-09T04:59:59Z", true, START_HOUR, END_HOUR);
        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1));

        assertThat(result.triggered()).isTrue();
        assertThat(result.score()).isEqualTo(WEIGHT);
    }

    @Test
    @DisplayName("Boundary hour 05:00 (exact window end) does not trigger even with high amount")
    void shouldNotTriggerAtExactEndHour() {
        UnusualHoursHighAmountRule rule = createRule("2026-11-09T05:00:00Z", true, START_HOUR, END_HOUR);
        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1));

        assertThat(result.triggered()).isFalse();
    }

    @Test
    @DisplayName("Boundary amount: equal to threshold in unusual window does not trigger")
    void shouldNotTriggerWhenAmountEqualsThresholdDuringUnusualHours() {
        UnusualHoursHighAmountRule rule = createRule("2026-11-09T02:00:00Z", true, START_HOUR, END_HOUR);
        RuleResult result = rule.evaluate(createTransaction(THRESHOLD));

        assertThat(result.triggered()).isFalse();
    }

    @Test
    @DisplayName("Boundary amount: 1 paise above threshold in unusual window triggers")
    void shouldTriggerWhenAmountIsAboveThresholdDuringUnusualHours() {
        UnusualHoursHighAmountRule rule = createRule("2026-11-09T02:00:00Z", true, START_HOUR, END_HOUR);
        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1));

        assertThat(result.triggered()).isTrue();
        assertThat(result.score()).isEqualTo(WEIGHT);
    }

    @Test
    @DisplayName("Should not trigger when rule is disabled")
    void shouldNotTriggerWhenDisabled() {
        UnusualHoursHighAmountRule rule = createRule("2026-11-09T02:00:00Z", false, START_HOUR, END_HOUR);
        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 100_000L));

        assertThat(result.triggered()).isFalse();
    }

    @Test
    @DisplayName("Should support daytime non-overnight window (e.g. 1 to 4)")
    void shouldSupportNonOvernightWindow() {
        // window 1:00 to 4:00
        UnusualHoursHighAmountRule ruleInside = createRule("2026-11-09T02:00:00Z", true, 1, 4);
        assertThat(ruleInside.evaluate(createTransaction(THRESHOLD + 1)).triggered()).isTrue();

        UnusualHoursHighAmountRule ruleOutside = createRule("2026-11-09T04:00:00Z", true, 1, 4);
        assertThat(ruleOutside.evaluate(createTransaction(THRESHOLD + 1)).triggered()).isFalse();
    }

    @Test
    @DisplayName("Should safely handle null transaction")
    void shouldHandleNullTransactionSafely() {
        UnusualHoursHighAmountRule rule = createRule("2026-11-09T02:00:00Z", true, START_HOUR, END_HOUR);
        RuleResult result = rule.evaluate(null);

        assertThat(result.triggered()).isFalse();
    }
}
