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
    private static final long MAX_FUTURE_SKEW = 300L; // 5 minutes
    private static final long MAX_PAST_AGE = 86400L; // 24 hours

    private FraudGuardProperties buildProperties(boolean enabled, int start, int end, String timeZone) {
        return new FraudGuardProperties(
                new FraudGuardProperties.ThresholdProperties(40, 70),
                new FraudGuardProperties.RulesProperties(
                        null,
                        new FraudGuardProperties.UnusualHoursRuleProperties(
                                enabled, start, end, THRESHOLD, WEIGHT, REASON_CODE, timeZone,
                                MAX_FUTURE_SKEW, MAX_PAST_AGE
                        )
                )
        );
    }

    private Transaction createTransaction(long amountPaise, Instant timestamp) {
        return new Transaction(
                "evt-test",
                "wallet-src",
                "wallet-dst",
                amountPaise,
                "TRANSFER",
                timestamp
        );
    }

    @Test
    @DisplayName("Boundary hour 22:59 event time (before window 23:00) does not trigger even with high amount")
    void shouldNotTriggerBeforeStartHour() {
        Instant eventTime = Instant.parse("2026-11-09T22:59:59Z");
        Clock clock = Clock.fixed(eventTime.plusSeconds(10), ZoneOffset.UTC);
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "UTC"), clock
        );

        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1, eventTime));

        assertThat(result.triggered()).isFalse();
    }

    @Test
    @DisplayName("Boundary hour 23:00 event time (exact window start) triggers when amount exceeds threshold")
    void shouldTriggerAtExactStartHour() {
        Instant eventTime = Instant.parse("2026-11-09T23:00:00Z");
        Clock clock = Clock.fixed(eventTime.plusSeconds(5), ZoneOffset.UTC);
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "UTC"), clock
        );

        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1, eventTime));

        assertThat(result.triggered()).isTrue();
        assertThat(result.score()).isEqualTo(WEIGHT);
        assertThat(result.reasonCode()).isEqualTo(REASON_CODE);
        assertThat(result.ruleName()).isEqualTo(UnusualHoursHighAmountRule.RULE_NAME);
    }

    @Test
    @DisplayName("Boundary hour 04:59 event time (last minute of unusual window) triggers when amount exceeds threshold")
    void shouldTriggerAtEndOfOvernightWindow() {
        Instant eventTime = Instant.parse("2026-11-09T04:59:59Z");
        Clock clock = Clock.fixed(eventTime.plusSeconds(2), ZoneOffset.UTC);
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "UTC"), clock
        );

        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1, eventTime));

        assertThat(result.triggered()).isTrue();
        assertThat(result.score()).isEqualTo(WEIGHT);
    }

    @Test
    @DisplayName("Boundary hour 05:00 event time (exact window end) does not trigger even with high amount")
    void shouldNotTriggerAtExactEndHour() {
        Instant eventTime = Instant.parse("2026-11-09T05:00:00Z");
        Clock clock = Clock.fixed(eventTime.plusSeconds(1), ZoneOffset.UTC);
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "UTC"), clock
        );

        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1, eventTime));

        assertThat(result.triggered()).isFalse();
    }

    @Test
    @DisplayName("Boundary amount: equal to threshold in unusual window does not trigger")
    void shouldNotTriggerWhenAmountEqualsThresholdDuringUnusualHours() {
        Instant eventTime = Instant.parse("2026-11-09T02:00:00Z");
        Clock clock = Clock.fixed(eventTime.plusSeconds(1), ZoneOffset.UTC);
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "UTC"), clock
        );

        RuleResult result = rule.evaluate(createTransaction(THRESHOLD, eventTime));

        assertThat(result.triggered()).isFalse();
    }

    @Test
    @DisplayName("Boundary amount: 1 paise above threshold in unusual window triggers")
    void shouldTriggerWhenAmountIsAboveThresholdDuringUnusualHours() {
        Instant eventTime = Instant.parse("2026-11-09T02:00:00Z");
        Clock clock = Clock.fixed(eventTime.plusSeconds(1), ZoneOffset.UTC);
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "UTC"), clock
        );

        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1, eventTime));

        assertThat(result.triggered()).isTrue();
        assertThat(result.score()).isEqualTo(WEIGHT);
    }

    @Test
    @DisplayName("Timezone conversion: UTC event time correctly evaluated against Asia/Kolkata (IST = UTC+05:30)")
    void shouldCorrectlyEvaluateInConfiguredTimezone() {
        // 18:00 UTC = 23:30 Asia/Kolkata (which falls in 23:00 - 05:00 window)
        Instant eventTime = Instant.parse("2026-11-09T18:00:00Z");
        Clock clock = Clock.fixed(eventTime.plusSeconds(5), ZoneOffset.UTC);
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "Asia/Kolkata"), clock
        );

        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1, eventTime));

        assertThat(result.triggered()).isTrue();
    }

    @Test
    @DisplayName("Staleness check: reject future timestamp that exceeds max allowed clock skew")
    void shouldRejectFutureTimestampBeyondAllowedSkew() {
        Instant processingTime = Instant.parse("2026-11-09T02:00:00Z");
        Clock clock = Clock.fixed(processingTime, ZoneOffset.UTC);
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "UTC"), clock
        );

        // Event time is 301 seconds into the future (exceeds max skew of 300s)
        Instant futureEventTime = processingTime.plusSeconds(MAX_FUTURE_SKEW + 1);
        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1, futureEventTime));

        assertThat(result.triggered()).isFalse();
    }

    @Test
    @DisplayName("Staleness check: accept future timestamp within allowed clock skew")
    void shouldAcceptFutureTimestampWithinAllowedSkew() {
        Instant processingTime = Instant.parse("2026-11-09T02:00:00Z");
        Clock clock = Clock.fixed(processingTime, ZoneOffset.UTC);
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "UTC"), clock
        );

        // Event time is 60 seconds into the future (within max skew of 300s)
        Instant futureEventTime = processingTime.plusSeconds(60);
        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1, futureEventTime));

        assertThat(result.triggered()).isTrue();
    }

    @Test
    @DisplayName("Staleness check: reject historical timestamp older than max allowed age")
    void shouldRejectTimestampOlderThanMaxAge() {
        Instant processingTime = Instant.parse("2026-11-10T12:00:00Z");
        Clock clock = Clock.fixed(processingTime, ZoneOffset.UTC);
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "UTC"), clock
        );

        // Event timestamp was at 02:00 AM yesterday (25 hours ago, exceeding 24 hours max age)
        Instant staleEventTime = processingTime.minusSeconds(MAX_PAST_AGE + 3600);
        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1, staleEventTime));

        assertThat(result.triggered()).isFalse();
    }

    @Test
    @DisplayName("Staleness check: accept timestamp within max allowed age")
    void shouldAcceptTimestampWithinMaxAge() {
        Instant processingTime = Instant.parse("2026-11-09T04:00:00Z");
        Clock clock = Clock.fixed(processingTime, ZoneOffset.UTC);
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "UTC"), clock
        );

        // Event timestamp was 2 hours ago (at 02:00 UTC, within unusual hours and within 24h)
        Instant validEventTime = processingTime.minusSeconds(7200);
        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 1, validEventTime));

        assertThat(result.triggered()).isTrue();
    }

    @Test
    @DisplayName("Should not trigger when rule is disabled")
    void shouldNotTriggerWhenDisabled() {
        Instant eventTime = Instant.parse("2026-11-09T02:00:00Z");
        Clock clock = Clock.fixed(eventTime, ZoneOffset.UTC);
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(false, START_HOUR, END_HOUR, "UTC"), clock
        );

        RuleResult result = rule.evaluate(createTransaction(THRESHOLD + 100_000L, eventTime));

        assertThat(result.triggered()).isFalse();
    }

    @Test
    @DisplayName("Should safely handle null transaction or null timestamp")
    void shouldHandleNullTransactionSafely() {
        Clock clock = Clock.systemUTC();
        UnusualHoursHighAmountRule rule = new UnusualHoursHighAmountRule(
                buildProperties(true, START_HOUR, END_HOUR, "UTC"), clock
        );

        assertThat(rule.evaluate(null).triggered()).isFalse();
        assertThat(rule.evaluate(createTransaction(THRESHOLD + 1, null)).triggered()).isFalse();
    }
}
