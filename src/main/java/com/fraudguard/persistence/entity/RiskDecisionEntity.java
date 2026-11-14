package com.fraudguard.persistence.entity;

import com.fraudguard.domain.model.RiskDecision;
import com.fraudguard.domain.model.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "risk_decisions")
public class RiskDecisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "source_wallet_id", nullable = false, updatable = false)
    private UUID sourceWalletId;

    @Column(name = "destination_wallet_id", nullable = false, updatable = false)
    private UUID destinationWalletId;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20, updatable = false)
    private TransactionType transactionType;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "event_timestamp", nullable = false, updatable = false)
    private Instant eventTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private RiskDecision decision;

    @Column
    private Short score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reason_codes", nullable = false, columnDefinition = "jsonb")
    private List<String> reasonCodes = List.of();

    @Column(nullable = false)
    private boolean degraded;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    protected RiskDecisionEntity() {
    }

    public RiskDecisionEntity(
            UUID eventId,
            String requestHash,
            UUID sourceWalletId,
            UUID destinationWalletId,
            long amountPaise,
            TransactionType transactionType,
            String currency,
            Instant eventTimestamp
    ) {
        this.eventId = eventId;
        this.requestHash = requestHash;
        this.sourceWalletId = sourceWalletId;
        this.destinationWalletId = destinationWalletId;
        this.amountPaise = amountPaise;
        this.transactionType = transactionType;
        this.currency = currency;
        this.eventTimestamp = eventTimestamp;
    }

    public void complete(
            RiskDecision decision,
            int score,
            List<String> reasonCodes,
            boolean degraded,
            Instant evaluatedAt
    ) {
        if (this.decision != null) {
            throw new IllegalStateException("Risk decision is already completed");
        }
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Risk score must be between 0 and 100");
        }
        this.decision = decision;
        this.score = (short) score;
        this.reasonCodes = List.copyOf(reasonCodes);
        this.degraded = degraded;
        this.evaluatedAt = evaluatedAt;
    }

    public Long getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getRequestHash() { return requestHash; }
    public UUID getSourceWalletId() { return sourceWalletId; }
    public UUID getDestinationWalletId() { return destinationWalletId; }
    public long getAmountPaise() { return amountPaise; }
    public TransactionType getTransactionType() { return transactionType; }
    public String getCurrency() { return currency; }
    public Instant getEventTimestamp() { return eventTimestamp; }
    public RiskDecision getDecision() { return decision; }
    public Short getScore() { return score; }
    public List<String> getReasonCodes() { return reasonCodes; }
    public boolean isDegraded() { return degraded; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
}