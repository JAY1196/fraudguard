package com.fraudguard.api.service;

import com.fraudguard.api.dto.DecisionPageResponse;
import com.fraudguard.api.dto.DecisionSummaryResponse;
import com.fraudguard.exception.InvalidRequestParameterException;
import com.fraudguard.persistence.entity.RiskDecisionEntity;
import com.fraudguard.persistence.repository.DecisionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class DecisionQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final DecisionRepository decisionRepository;
    private final DecisionCursorCodec cursorCodec;

    public DecisionQueryService(
            DecisionRepository decisionRepository,
            DecisionCursorCodec cursorCodec
    ) {
        this.decisionRepository = decisionRepository;
        this.cursorCodec = cursorCodec;
    }

    @Transactional(readOnly = true)
    public DecisionPageResponse getWalletDecisions(
            UUID walletId,
            Integer requestedLimit,
            String requestedCursor
    ) {
        if (walletId == null) {
            throw new InvalidRequestParameterException("Wallet ID is required");
        }

        int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;

        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestParameterException(
                    "Limit must be between 1 and " + MAX_LIMIT
            );
        }

        Long cursor = cursorCodec.decode(requestedCursor);
        var pageable = PageRequest.of(0, limit + 1);

        List<RiskDecisionEntity> sourceDecisions = cursor == null
                ? decisionRepository.findBySourceWalletIdOrderByIdDesc(walletId, pageable)
                : decisionRepository.findBySourceWalletIdAndIdLessThanOrderByIdDesc(
                        walletId, cursor, pageable
                );

        List<RiskDecisionEntity> destinationDecisions = cursor == null
                ? decisionRepository.findByDestinationWalletIdAndSourceWalletIdNotOrderByIdDesc(
                        walletId, walletId, pageable
                )
                : decisionRepository.findByDestinationWalletIdAndSourceWalletIdNotAndIdLessThanOrderByIdDesc(
                        walletId, walletId, cursor, pageable
                );

        List<RiskDecisionEntity> fetched = new ArrayList<>(
                sourceDecisions.size() + destinationDecisions.size()
        );
        fetched.addAll(sourceDecisions);
        fetched.addAll(destinationDecisions);

        fetched.sort(Comparator.comparing(RiskDecisionEntity::getId).reversed());

        boolean hasMore = fetched.size() > limit;

        List<RiskDecisionEntity> page = fetched.stream()
                .limit(limit)
                .toList();

        List<DecisionSummaryResponse> decisions = page.stream()
                .map(this::toSummary)
                .toList();

        String nextCursor = hasMore && !decisions.isEmpty()
                ? cursorCodec.encode(decisions.get(decisions.size() - 1).id())
                : null;

        return new DecisionPageResponse(decisions, nextCursor, hasMore);
    }

    private DecisionSummaryResponse toSummary(RiskDecisionEntity entity) {
        return new DecisionSummaryResponse(
                entity.getId(),
                entity.getEventId(),
                entity.getSourceWalletId(),
                entity.getDestinationWalletId(),
                entity.getAmountPaise(),
                entity.getTransactionType(),
                entity.getCurrency(),
                entity.getEventTimestamp(),
                entity.getDecision(),
                entity.getScore(),
                entity.getReasonCodes(),
                entity.isDegraded(),
                entity.getEvaluatedAt()
        );
    }
}