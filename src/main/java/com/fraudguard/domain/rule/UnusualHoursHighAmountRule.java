package com.fraudguard.domain.rule;

import com.fraudguard.config.FraudGuardProperties;
import com.fraudguard.domain.model.RuleResult;
import com.fraudguard.domain.model.Transaction;
import org.springframework.stereotype.Component;

import java.time.Clock;
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
        if (transaction == null) {
            return RuleResult.notTriggered(RULE_NAME);
        }

        var config = properties != null && properties.rules() != null
                ? properties.rules().unusualHours()
                : null;

        if (config == null || !config.enabled()) {
            return RuleResult.notTriggered(RULE_NAME);
        }

        Instant now = clock.instant();
        ZoneId zoneId = ZoneId.of(config.timeZone() != null ? config.timeZone() : "UTC");
        int currentHour = now.atZone(zoneId).getHour();

        boolean isUnusualHour;
        if (config.startHour() <= config.endHour()) {
            isUnusualHour = currentHour >= config.startHour() && currentHour < config.endHour();
        } else {
            // Spans midnight, e.g., 23 to 5
            isUnusualHour = currentHour >= config.startHour() || currentHour < config.endHour();
        }

        boolean isHighAmount = transaction.amountPaise() > config.thresholdPaise();

        if (isUnusualHour && isHighAmount) {
            return RuleResult.triggered(RULE_NAME, config.weight(), config.reasonCode());
        }

        return RuleResult.notTriggered(RULE_NAME);
    }
}
