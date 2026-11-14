package com.fraudguard.api.service;

import com.fraudguard.api.dto.EvaluateRiskRequest;
import com.fraudguard.api.dto.EvaluateRiskResponse;
import com.fraudguard.api.validator.TransactionTimestampValidator;
import com.fraudguard.domain.engine.RuleEngine;
import com.fraudguard.domain.model.EvaluationResult;
import com.fraudguard.domain.model.Transaction;
import com.fraudguard.domain.service.RequestHashService;
import com.fraudguard.exception.EventPayloadMismatchException;
import com.fraudguard.persistence.entity.RiskDecisionEntity;
import com.fraudguard.persistence.repository.DecisionClaimRepository;
import com.fraudguard.persistence.repository.DecisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class RiskEvaluationService {

    private final RuleEngine ruleEngine;
    private final TransactionTimestampValidator timestampValidator;
    private final RequestHashService requestHashService;
    private final DecisionClaimRepository claimRepository;
    private final DecisionRepository decisionRepository;
    private final Clock clock;

    public RiskEvaluationService(
            RuleEngine ruleEngine,
            TransactionTimestampValidator timestampValidator,
            RequestHashService requestHashService,
            DecisionClaimRepository claimRepository,
            DecisionRepository decisionRepository,
            Clock clock
    ) {
        this.ruleEngine = ruleEngine;
        this.timestampValidator = timestampValidator;
        this.requestHashService = requestHashService;
        this.claimRepository = claimRepository;
        this.decisionRepository = decisionRepository;
        this.clock = clock;
    }

    @Transactional
    public EvaluateRiskResponse evaluate(EvaluateRiskRequest request) {
        String requestHash = requestHashService.hash(request);

        var claimedId = claimRepository.claim(
                request.eventId(),
                requestHash,
                request.sourceWalletId(),
                request.destinationWalletId(),
                request.amountPaise(),
                request.type().name(),
                request.currency(),
                request.timestamp()
        );

        if (claimedId.isEmpty()) {
            RiskDecisionEntity existing = decisionRepository
                    .findByEventId(request.eventId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Existing event claim could not be loaded: " + request.eventId()
                    ));

            if (!existing.getRequestHash().equals(requestHash)) {
                throw new EventPayloadMismatchException(
                        "Event ID was already used with a different transaction payload"
                );
            }

            if (existing.getDecision() == null) {
                throw new IllegalStateException(
                        "A committed risk decision is unexpectedly incomplete: " + request.eventId()
                );
            }

            return toResponse(existing);
        }

        timestampValidator.validate(request.timestamp());

        RiskDecisionEntity entity = decisionRepository.findById(claimedId.get())
                .orElseThrow(() -> new IllegalStateException(
                        "Newly claimed risk decision could not be loaded: " + request.eventId()
                ));

        Transaction transaction = new Transaction(
                request.eventId(),
                request.sourceWalletId(),
                request.destinationWalletId(),
                request.amountPaise(),
                request.type(),
                request.currency(),
                request.timestamp()
        );

        EvaluationResult result = ruleEngine.evaluate(transaction);

        entity.complete(
                result.decision(),
                result.score(),
                result.reasonCodes(),
                result.degraded(),
                clock.instant()
        );

        return toResponse(entity);
    }

    private EvaluateRiskResponse toResponse(RiskDecisionEntity entity) {
        return new EvaluateRiskResponse(
                entity.getEventId(),
                entity.getDecision(),
                entity.getScore(),
                entity.getReasonCodes(),
                entity.isDegraded()
        );
    }
}