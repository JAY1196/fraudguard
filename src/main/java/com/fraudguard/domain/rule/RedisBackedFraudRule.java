package com.fraudguard.domain.rule;

/**
 * Marker for fraud rules whose evaluation depends on Redis state.
 */
public interface RedisBackedFraudRule extends FraudRule {
}