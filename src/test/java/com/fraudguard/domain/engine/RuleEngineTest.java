package com.fraudguard.domain.engine;

import com.fraudguard.config.FraudGuardProperties;
import com.fraudguard.domain.model.EvaluationResult;
import com.fraudguard.domain.model.RiskDecision;
import com.fraudguard.domain.model.RuleResult;
import com.fraudguard.domain.model.Transaction;
import com.fraudguard.domain.rule.FraudRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleEngineTest {

    @Mock
    private FraudRule rule1;

    @Mock
    private FraudRule rule2;

    @Mock
    private FraudRule rule3;

    private Clock fixedClock;
    private FraudGuardProperties properties;
    private Transaction sampleTransaction;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC);
        properties = new FraudGuardProperties(
                new FraudGuardProperties.ThresholdProperties(40, 70),
                null
        );
        sampleTransaction = new Transaction(
                "evt-100",
                "wallet-src-1",
                "wallet-dst-1",
                50000L,
                "TRANSFER",
                fixedClock.instant()
        );
    }

    @Test
    @DisplayName("Should return ALLOW when no rules trigger")
    void shouldReturnAllowWhenNoRulesTrigger() {
        when(rule1.evaluate(any())).thenReturn(RuleResult.notTriggered("Rule1"));
        when(rule2.evaluate(any())).thenReturn(RuleResult.notTriggered("Rule2"));

        RuleEngine engine = new RuleEngine(List.of(rule1, rule2), properties, fixedClock);
        EvaluationResult result = engine.evaluate(sampleTransaction);

        assertThat(result.decision()).isEqualTo(RiskDecision.ALLOW);
        assertThat(result.score()).isZero();
        assertThat(result.reasonCodes()).isEmpty();
        verify(rule1).evaluate(sampleTransaction);
        verify(rule2).evaluate(sampleTransaction);
    }

    @Test
    @DisplayName("Should return ALLOW at boundary score of 39 (below review threshold 40)")
    void shouldReturnAllowBelowReviewThresholdBoundary() {
        when(rule1.evaluate(any())).thenReturn(RuleResult.triggered("Rule1", 39, "LOW_RISK"));

        RuleEngine engine = new RuleEngine(List.of(rule1), properties, fixedClock);
        EvaluationResult result = engine.evaluate(sampleTransaction);

        assertThat(result.decision()).isEqualTo(RiskDecision.ALLOW);
        assertThat(result.score()).isEqualTo(39);
        assertThat(result.reasonCodes()).containsExactly("LOW_RISK");
    }

    @Test
    @DisplayName("Should return REVIEW at exact review threshold boundary of 40")
    void shouldReturnReviewAtExactReviewThresholdBoundary() {
        when(rule1.evaluate(any())).thenReturn(RuleResult.triggered("Rule1", 40, "MEDIUM_RISK"));

        RuleEngine engine = new RuleEngine(List.of(rule1), properties, fixedClock);
        EvaluationResult result = engine.evaluate(sampleTransaction);

        assertThat(result.decision()).isEqualTo(RiskDecision.REVIEW);
        assertThat(result.score()).isEqualTo(40);
        assertThat(result.reasonCodes()).containsExactly("MEDIUM_RISK");
    }

    @Test
    @DisplayName("Should return REVIEW at boundary score of 69 (below block threshold 70)")
    void shouldReturnReviewBelowBlockThresholdBoundary() {
        when(rule1.evaluate(any())).thenReturn(RuleResult.triggered("Rule1", 35, "RISK_A"));
        when(rule2.evaluate(any())).thenReturn(RuleResult.triggered("Rule2", 34, "RISK_B"));

        RuleEngine engine = new RuleEngine(List.of(rule1, rule2), properties, fixedClock);
        EvaluationResult result = engine.evaluate(sampleTransaction);

        assertThat(result.decision()).isEqualTo(RiskDecision.REVIEW);
        assertThat(result.score()).isEqualTo(69);
    }

    @Test
    @DisplayName("Should return BLOCK at exact block threshold boundary of 70")
    void shouldReturnBlockAtExactBlockThresholdBoundary() {
        when(rule1.evaluate(any())).thenReturn(RuleResult.triggered("Rule1", 70, "HIGH_RISK"));

        RuleEngine engine = new RuleEngine(List.of(rule1), properties, fixedClock);
        EvaluationResult result = engine.evaluate(sampleTransaction);

        assertThat(result.decision()).isEqualTo(RiskDecision.BLOCK);
        assertThat(result.score()).isEqualTo(70);
        assertThat(result.reasonCodes()).containsExactly("HIGH_RISK");
    }

    @Test
    @DisplayName("Should cap score at 100 when raw score exceeds 100")
    void shouldCapScoreAt100WhenScoreExceeds100() {
        when(rule1.evaluate(any())).thenReturn(RuleResult.triggered("Rule1", 60, "CODE_A"));
        when(rule2.evaluate(any())).thenReturn(RuleResult.triggered("Rule2", 55, "CODE_B"));

        RuleEngine engine = new RuleEngine(List.of(rule1, rule2), properties, fixedClock);
        EvaluationResult result = engine.evaluate(sampleTransaction);

        assertThat(result.decision()).isEqualTo(RiskDecision.BLOCK);
        assertThat(result.score()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should sort reason codes deterministically and eliminate duplicates")
    void shouldSortReasonCodesAlphabeticallyAndDeduplicate() {
        when(rule1.evaluate(any())).thenReturn(RuleResult.triggered("Rule1", 20, "VELOCITY_SPIKE"));
        when(rule2.evaluate(any())).thenReturn(RuleResult.triggered("Rule2", 20, "AMOUNT_ANOMALY"));
        when(rule3.evaluate(any())).thenReturn(RuleResult.triggered("Rule3", 20, "AMOUNT_ANOMALY"));

        RuleEngine engine = new RuleEngine(List.of(rule1, rule2, rule3), properties, fixedClock);
        EvaluationResult result = engine.evaluate(sampleTransaction);

        assertThat(result.reasonCodes())
                .containsExactly("AMOUNT_ANOMALY", "VELOCITY_SPIKE");
    }

    @Test
    @DisplayName("Should handle empty rules list safely")
    void shouldHandleEmptyRulesListSafely() {
        RuleEngine engine = new RuleEngine(List.of(), properties, fixedClock);
        EvaluationResult result = engine.evaluate(sampleTransaction);

        assertThat(result.decision()).isEqualTo(RiskDecision.ALLOW);
        assertThat(result.score()).isZero();
        assertThat(result.reasonCodes()).isEmpty();
    }
}
