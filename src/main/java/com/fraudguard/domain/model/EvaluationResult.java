package com.fraudguard.domain.model;

import java.util.List;

public record EvaluationResult(
        RiskDecision decision,
        int score,
        List<String> reasonCodes
) {}
