package com.fraudguard.domain.rule;

import com.fraudguard.config.FraudGuardProperties;
import com.fraudguard.domain.model.RuleResult;
import com.fraudguard.domain.model.Transaction;
import com.fraudguard.domain.model.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UnusualHoursHighAmountRuleTest {

    private static final long THRESHOLD = 2_000_000L; // 20,000 INR
    private static final int WEIGHT = 40;
    private static final String REASON_CODE = "UNUSUAL_HOURS_HIGH_AMOUNT";
    private static final int START_HOUR = 23;
    private static final int END_HOUR = 5;
    private static final UUID EVENT_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID SRC_WALLET = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DST_WALLET = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private FraudGuardProperties buildProperties(boolean enabled, int start, int end, String timeZone) {
        return new FraudGuardProperties(
                new FraudGuardProperties.ThresholdProperties(40, 70),
                new FraudGuardProperties.RulesProperties(
                        null,
                        new FraudGuardProperties.UnusualHoursRuleProperties(
                                enabled, start, end, THRESHOLD, WEIGHT, REASON_CODE, timeZone
                        )
                ),
                null
        );
    }

    private Transaction createTransaction(long amountPaise, Instant timestamp) {
        return new Transaction(
                EVENT_ID,
                SRC_WALLET,
                DST_WALLET,
                amountPaise,
                TransactionType.TRANSFER,
                "INR",
                timestamp
        );
    }

    @Test
    @DisplayName("Boundary hour 22:59 event time (before window 23:00) does not trigger even with high amount")
    void shouldNotTriggerBeforeStartHour() {
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "UTC")
        );
        Instant eventTime = Instant.parse("2026-11-09T22:59:59Z");

        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1, eventTime));

        assertThat(result.triggered()).isFalse();
    }

    @Test
    @DisplayName("Boundary hour 23:00 event time (exact window start) triggers when amount exceeds threshold")
    void shouldTriggerAtExactStartHour() {
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "UTC")
        );
        Instant eventTime = Instant.parse("2026-11-09T23:00:00Z");

        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1, eventTime));

        assertThat(result.triggered()).isTrue();
        assertThat(result.score()).isEqualTo(WEIGHT);
        assertThat(result.reasonCode()).isEqualTo(REASON_CODE);
        assertThat(result.ruleName()).isEqualTo(UnusualHoursHighAmountRule.RULE_NAME);
    }

    @Test
    @DisplayName("Boundary hour 04:59 event time (last minute of unusual window) triggers when amount exceeds threshold")
    void shouldTriggerAtEndOfOvernightWindow() {
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "UTC")
        );
        Instant eventTime = Instant.parse("2026-11-09T04:59:59Z");

        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1, eventTime));

        assertThat(result.triggered()).isTrue();
        assertThat(result.score()).isEqualTo(WEIGHT);
    }

    @Test
    @DisplayName("Boundary hour 05:00 event time (exact window end) does not trigger even with high amount")
    void shouldNotTriggerAtExactEndHour() {
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "UTC")
        );
        Instant eventTime = Instant.parse("2026-11-09T05:00:00Z");

        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1, eventTime));

        assertThat(result.triggered()).isFalse();
    }

    @Test
    @DisplayName("Boundary amount: equal to threshold in unusual window does not trigger")
    void shouldNotTriggerWhenAmountEqualsThresholdDuringUnusualHours() {
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "UTC")
        );
        Instant eventTime = Instant.parse("2026-11-09T02:00:00Z");

        RuleResult result = rule.evaluate(createTransaction(THRESHOLD, eventTime));

        assertThat(result.triggered()).isFalse();
    }

    @Test
    @DisplayName("Boundary amount: 1 paise above threshold in unusual window triggers")
    void shouldTriggerWhenAmountIsAboveThresholdDuringUnusualHours() {
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "UTC")
        );
        Instant eventTime = Instant.parse("2026-11-09T02:00:00Z");

        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1, eventTime));

        assertThat(result.triggered()).isTrue();
        assertThat(result.score()).isEqualTo(WEIGHT);
    }

    @Test
    @DisplayName("Timezone conversion: UTC event time correctly evaluated against Asia/Kolkata (IST = UTC+05:30)")
    void shouldCorrectlyEvaluateInConfiguredTimezone() {
        // 18:00 UTC = 23:30 Asia/Kolkata (which falls in 23:00 - 05:00 window)
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "Asia/Kolkata")
        );
        Instant eventTime = Instant.parse("2026-11-09T18:00:00Z");

        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1, eventTime));

        assertThat(result.triggered()).isTrue();
    }

    @Test
    @DisplayName("Should support daytime non-overnight window (e.g. 1 to 4)")
    void shouldSupportNonOvernightWindow() {
        UnusualHoursHighAmountRule ruleInside = new UnusualHoursHighAmountRule(
                buildProperties(true, 1, 4, "UTC")
        );
        assertThat(ruleInside.evaluate(createTransaction(THRESHOLD + 1, Instant.parse("2026-11-09T02:00:00Z"))).triggered()).isTrue();

        UnusualHoursHighAmountRule ruleOutside = new UnusualHoursHighAmountRule(
                buildProperties(true, 1, 4, "UTC")
        );
        assertThat(ruleOutside.evaluate(createTransaction(THRESHOLD + 1, Instant.parse("2026-11-09T04:00:00Z"))).triggered()).isFalse();
    }

    @Test
    @DisplayName("Should not trigger when rule is disabled")
    void shouldNotTriggerWhenDisabled() {
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(false, START_HOUR, END_HOUR, "UTC")
        );
        Instant eventTime = Instant.parse("2026-11-09T02:00:00Z");

        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 100_000L, eventTime));

        assertThat(result.triggered()).isFalse();
    }

    @Test
    @DisplayName("Should safely handle null transaction or null timestamp")
    void shouldHandleNullTransactionSafely() {
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "UTC")
        );

        assertThat(rule.evaluate(null).triggered()).isFalse();
        assertThat(rule.evaluate(createTransaction(THRESHOLD + 1, null)).triggered()).isFalse();
    }
}
