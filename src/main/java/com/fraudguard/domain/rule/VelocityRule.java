package com.fraudguard.domain.rule;

import com.fraudguard.config.FraudGuardProperties;
import com.fraudguard.domain.model.RuleResult;
import com.fraudguard.domain.model.Transaction;
import com.fraudguard.domain.model.TransactionType;
import com.fraudguard.domain.store.StateStore;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class VelocityRule implements RedisBackedFraudRule {

    public static final String RULE_NAME = "VELOCITY";

    private static final String REDIS_KEY_PREFIX = "fraudguard:velocity:";
    private static final UUID SYSTEM_WALLET_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final FraudGuardProperties properties;
    private final StateStore stateStore;

    public VelocityRule(FraudGuardProperties properties, StateStore stateStore) {
        this.properties = properties;
        this.stateStore = stateStore;
    }

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public RuleResult evaluate(Transaction transaction) {
        if (transaction == null
                || transaction.eventId() == null
                || transaction.timestamp() == null
                || transaction.type() == null) {
            return RuleResult.notTriggered(RULE_NAME);
        }

        var config = properties != null && properties.rules() != null
                ? properties.rules().velocity()
                : null;

        if (config == null || !config.enabled()) {
            return RuleResult.notTriggered(RULE_NAME);
        }

        UUID actorWalletId = resolveActorWallet(transaction);

        if (actorWalletId == null || SYSTEM_WALLET_ID.equals(actorWalletId)) {
            return RuleResult.notTriggered(RULE_NAME);
        }

        String redisKey = REDIS_KEY_PREFIX + actorWalletId;
        long eventTimestampMs = transaction.timestamp().toEpochMilli();
        long windowMs = config.windowSeconds() * 1000L;

        long transactionCount = stateStore.recordAndCountSlidingWindow(
                redisKey,
                transaction.eventId().toString(),
                eventTimestampMs,
                windowMs,
                config.ttlSeconds()
        );

        if (transactionCount > config.maxTransactionsInWindow()) {
            return RuleResult.triggered(
                    RULE_NAME,
                    config.weight(),
                    config.reasonCode()
            );
        }

        return RuleResult.notTriggered(RULE_NAME);
    }

    private UUID resolveActorWallet(Transaction transaction) {
        TransactionType type = transaction.type();

        return switch (type) {
            case TRANSFER, WITHDRAWAL -> transaction.sourceWalletId();
            case TOPUP -> transaction.destinationWalletId();
            case REVERSAL -> null;
        };
    }
}