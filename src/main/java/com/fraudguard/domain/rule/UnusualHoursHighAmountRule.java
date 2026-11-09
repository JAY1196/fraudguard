package com.fraudguard.domain.rule;

import com.fraudguard.config.FraudGuardProperties;
import com.fraudguard.domain.model.RuleResult;
import com.fraudguard.domain.model.Transaction;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

@Component
public class UnusualHoursHighAmountRule implements FraudRule {

    public static final String RULE_NAME = "UNUSUAL_HOURS_HIGH_AMOUNT";

    private final FraudGuardProperties properties;
    private final Clock clock;

    public UnusualHoursHighAmountRule(FraudGuardProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public RuleResult evaluate(Transaction transaction) {
        if (transaction == null || transaction.timestamp() == null) {
            return RuleResult.notTriggered(RULE_NAME);
        }

        var config = properties != null && properties.rules() != null
                ? properties.rules().unusualHours()
                : null;

        if (config == null || !config.enabled()) {
            return RuleResult.notTriggered(RULE_NAME);
        }

        Instant eventTime = transaction.timestamp();
        Instant processingTime = clock.instant();

        // Staleness & Clock Skew Guard
        if (eventTime.isAfter(processingTime.plusSeconds(config.maxFutureSkewSeconds()))) {
            // Future timestamp beyond allowed clock skew: reject evaluation
            return RuleResult.notTriggered(RULE_NAME);
        }
        if (eventTime.isBefore(processingTime.minusSeconds(config.maxPastAgeSeconds()))) {
            // Stale timestamp older than allowed age: reject evaluation
            return RuleResult.notTriggered(RULE_NAME);
        }

        // Event time converted to configured timezone
        ZoneId zoneId = ZoneId.of(config.timeZone() != null ? config.timeZone() : "Asia/Kolkata");
        int eventHour = eventTime.atZone(zoneId).getHour();

        boolean isUnusualHour;
        if (config.startHour() <= config.endHour()) {
            isUnusualHour = eventHour >= config.startHour() && eventHour < config.endHour();
        } else {
            // Overnight window, e.g., 23 to 5
            isUnusualHour = eventHour >= config.startHour() || eventHour < config.endHour();
        }

        boolean isHighAmount = transaction.amountPaise() > config.thresholdPaise();

        if (isUnusualHour && isHighAmount) {
            return RuleResult.triggered(RULE_NAME, config.weight(), config.reasonCode());
        }

        return RuleResult.notTriggered(RULE_NAME);
    }
}
