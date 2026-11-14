package com.fraudguard.persistence.repository;

import com.fraudguard.PostgresTestcontainersConfiguration;
import com.fraudguard.domain.model.RiskDecision;
import com.fraudguard.domain.model.TransactionType;
import com.fraudguard.persistence.entity.RiskDecisionEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(PostgresTestcontainersConfiguration.class)
class DecisionRepositoryIntegrationTest {

    @Autowired
    private DecisionRepository decisionRepository;

    @Test
    @DisplayName("Persists a completed decision and retrieves it by event ID")
    void shouldPersistAndFindDecisionByEventId() {
        UUID eventId = UUID.randomUUID();

        RiskDecisionEntity entity = createDecision(eventId, "a".repeat(64));
        decisionRepository.saveAndFlush(entity);

        RiskDecisionEntity retrieved = decisionRepository.findByEventId(eventId)
                .orElseThrow();

        assertThat(retrieved.getEventId()).isEqualTo(eventId);
        assertThat(retrieved.getDecision()).isEqualTo(RiskDecision.ALLOW);
        assertThat(retrieved.getScore()).isEqualTo((short) 10);
        assertThat(retrieved.getReasonCodes())
                .containsExactly("LOW_RISK", "NORMAL_ACTIVITY");
        assertThat(retrieved.isDegraded()).isFalse();
        assertThat(retrieved.getEvaluatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Returns decisions in descending ID order using cursor pagination")
    void shouldReturnDecisionsUsingCursorPagination() {
        RiskDecisionEntity first = decisionRepository.saveAndFlush(
                createDecision(UUID.randomUUID(), "a".repeat(64))
        );
        RiskDecisionEntity second = decisionRepository.saveAndFlush(
                createDecision(UUID.randomUUID(), "b".repeat(64))
        );
        RiskDecisionEntity third = decisionRepository.saveAndFlush(
                createDecision(UUID.randomUUID(), "c".repeat(64))
        );

        List<RiskDecisionEntity> firstPage =
                decisionRepository.findAllByOrderByIdDesc(PageRequest.of(0, 2));

        assertThat(firstPage)
                .extracting(RiskDecisionEntity::getId)
                .containsExactly(third.getId(), second.getId());

        List<RiskDecisionEntity> secondPage =
                decisionRepository.findByIdLessThanOrderByIdDesc(
                        second.getId(),
                        PageRequest.of(0, 2)
                );

        assertThat(secondPage)
                .extracting(RiskDecisionEntity::getId)
                .containsExactly(first.getId());
    }

    private RiskDecisionEntity createDecision(UUID eventId, String requestHash) {
        Instant now = Instant.now();

        RiskDecisionEntity entity = new RiskDecisionEntity(
                eventId,
                requestHash,
                UUID.randomUUID(),
                UUID.randomUUID(),
                100_000L,
                TransactionType.TRANSFER,
                "INR",
                now
        );

        entity.complete(
                RiskDecision.ALLOW,
                10,
                List.of("LOW_RISK", "NORMAL_ACTIVITY"),
                false,
                now
        );

        return entity;
    }
}