package com.fraudguard.domain.rule;

import com.fraudguard.config.FraudGuardProperties;
import com.fraudguard.domain.model.RuleResult;
import com.fraudguard.domain.model.Transaction;
import org.springframework.stereotype.Component;

@Component
public class HighAmountRule implements FraudRule {

    public static final String RULE_NAME = "HIGH_AMOUNT";

    private final FraudGuardProperties properties;

    public HighAmountRule(FraudGuardProperties properties) {
        this.properties = properties;
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
                ? properties.rules().highAmount()
                : null;

        if (config == null || !config.enabled()) {
            return RuleResult.notTriggered(RULE_NAME);
        }

        if (transaction.amountPaise() > config.thresholdPaise()) {
            return RuleResult.triggered(RULE_NAME, config.weight(), config.reasonCode());
        }

        return RuleResult.notTriggered(RULE_NAME);
    }
}
