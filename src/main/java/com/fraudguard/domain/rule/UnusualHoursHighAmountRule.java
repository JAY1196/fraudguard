package com.fraudguard.domain.rule;

import com.fraudguard.config.FraudGuardProperties;
import com.fraudguard.domain.model.RuleResult;
import com.fraudguard.domain.model.Transaction;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;

@Component
public class UnusualHoursHighAmountRule implements FraudRule {

    public static final String RULE_NAME = "UNUSUAL_HOURS_HIGH_AMOUNT";

    private final FraudGuardProperties properties;

    public UnusualHoursHighAmountRule(FraudGuardProperties properties) {
        this.properties = properties;
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

        // Rule always evaluates event time converted to configured timezone
        Instant eventTime = transaction.timestamp();
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
