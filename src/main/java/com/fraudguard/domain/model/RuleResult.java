package com.fraudguard.domain.model;

public record RuleResult(
        String ruleName,
        boolean triggered,
        int score,
        String reasonCode
) {
    public static RuleResult triggered(String ruleName, int score, String reasonCode) {
        return new RuleResult(ruleName, true, score, reasonCode);
    }

    public static RuleResult notTriggered(String ruleName) {
        return new RuleResult(ruleName, false, 0, null);
    }
}
