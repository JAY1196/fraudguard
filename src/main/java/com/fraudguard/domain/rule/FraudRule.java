package com.fraudguard.domain.rule;

import com.fraudguard.domain.model.RuleResult;
import com.fraudguard.domain.model.Transaction;

public interface FraudRule {
    String getName();
    RuleResult evaluate(Transaction transaction);
}
