package com.fraudguard.domain.rule;

import com.fraudguard.config.FraudGuardProperties;
import com.fraudguard.domain.model.RuleResult;
import com.fraudguard.domain.model.Transaction;
import com.fraudguard.domain.model.TransactionType;
import com.fraudguard.domain.store.StateStore;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

@Component
public class AmountSpikeRule implements FraudRule {

    public static final String RULE_NAME = "AMOUNT_SPIKE";

    private static final String REDIS_KEY_PREFIX = "fraudguard:amount-spike:";
    private static final UUID SYSTEM_WALLET_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final FraudGuardProperties properties;
    private final StateStore stateStore;

    public AmountSpikeRule(FraudGuardProperties properties, StateStore stateStore) {
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
                || transaction.type() == null
                || transaction.amountPaise() <= 0) {
            return RuleResult.notTriggered(RULE_NAME);
        }

        var config = properties != null && properties.rules() != null
                ? properties.rules().amountSpike()
                : null;

        if (config == null || !config.enabled()) {
            return RuleResult.notTriggered(RULE_NAME);
        }

        UUID actorWalletId = resolveActorWallet(transaction);

        if (actorWalletId == null || SYSTEM_WALLET_ID.equals(actorWalletId)) {
            return RuleResult.notTriggered(RULE_NAME);
        }

        String redisKey = REDIS_KEY_PREFIX + "{" + actorWalletId + "}";

        List<Long> baseline = stateStore.recordAndGetPriorAmounts(
                redisKey,
                transaction.eventId().toString(),
                transaction.amountPaise(),
                transaction.timestamp().toEpochMilli(),
                config.historySize(),
                config.maxHistory(),
                config.ttlSeconds()
        );

        if (baseline == null || baseline.size() < config.minimumHistory()) {
            return RuleResult.notTriggered(RULE_NAME);
        }

        BigInteger sum = baseline.stream()
                .map(BigInteger::valueOf)
                .reduce(BigInteger.ZERO, BigInteger::add);

        BigDecimal scaledCurrentAmount = BigDecimal.valueOf(transaction.amountPaise())
                .multiply(BigDecimal.valueOf(baseline.size()));

        BigDecimal scaledThreshold = new BigDecimal(sum)
                .multiply(BigDecimal.valueOf(config.multiplier()));

        if (scaledCurrentAmount.compareTo(scaledThreshold) > 0) {
            return RuleResult.triggered(
                    RULE_NAME,
                    config.weight(),
                    config.reasonCode()
            );
        }

        return RuleResult.notTriggered(RULE_NAME);
    }

    private UUID resolveActorWallet(Transaction transaction) {
        return switch (transaction.type()) {
            case TRANSFER, WITHDRAWAL -> transaction.sourceWalletId();
            case TOPUP -> transaction.destinationWalletId();
            case REVERSAL -> null;
        };
    }
}