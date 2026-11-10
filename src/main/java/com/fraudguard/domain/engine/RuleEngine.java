package com.fraudguard.domain.engine;

import com.fraudguard.config.FraudGuardProperties;
import com.fraudguard.domain.model.EvaluationResult;
import com.fraudguard.domain.model.RiskDecision;
import com.fraudguard.domain.model.RuleResult;
import com.fraudguard.domain.model.Transaction;
import com.fraudguard.domain.rule.FraudRule;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

@Service
public class RuleEngine {

    private final List<FraudRule> rules;
    private final FraudGuardProperties properties;
    private final Clock clock;

    public RuleEngine(List<FraudRule> rules, FraudGuardProperties properties, Clock clock) {
        this.rules = rules != null ? List.copyOf(rules) : List.of();
        this.properties = properties;
        this.clock = clock;
    }

    public EvaluationResult evaluate(Transaction transaction) {
        List<RuleResult> results = rules.stream()
                .map(rule -> rule.evaluate(transaction))
                .toList();

        int rawScore = results.stream()
                .filter(RuleResult::triggered)
                .mapToInt(RuleResult::score)
                .sum();

        int cappedScore = Math.min(100, Math.max(0, rawScore));

        List<String> reasonCodes = results.stream()
                .filter(RuleResult::triggered)
                .map(RuleResult::reasonCode)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        int reviewThreshold = properties != null && properties.thresholds() != null
                ? properties.thresholds().review() : 40;
        int blockThreshold = properties != null && properties.thresholds() != null
                ? properties.thresholds().block() : 70;

        RiskDecision decision;
        if (cappedScore >= blockThreshold) {
            decision = RiskDecision.BLOCK;
        } else if (cappedScore >= reviewThreshold) {
            decision = RiskDecision.REVIEW;
        } else {
            decision = RiskDecision.ALLOW;
        }

        return new EvaluationResult(decision, cappedScore, reasonCodes);
    }

    public Clock getClock() {
        return clock;
    }
}
