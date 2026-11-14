package com.fraudguard.persistence.repository;

import com.fraudguard.persistence.entity.RiskDecisionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DecisionRepository extends JpaRepository<RiskDecisionEntity, Long> {

    Optional<RiskDecisionEntity> findByEventId(UUID eventId);

    List<RiskDecisionEntity> findAllByOrderByIdDesc(Pageable pageable);

    List<RiskDecisionEntity> findByIdLessThanOrderByIdDesc(
            Long cursor,
            Pageable pageable
    );

    List<RiskDecisionEntity> findBySourceWalletIdOrderByIdDesc(
            UUID sourceWalletId,
            Pageable pageable
    );

    List<RiskDecisionEntity> findBySourceWalletIdAndIdLessThanOrderByIdDesc(
            UUID sourceWalletId,
            Long cursor,
            Pageable pageable
    );

    List<RiskDecisionEntity> findByDestinationWalletIdAndSourceWalletIdNotOrderByIdDesc(
            UUID destinationWalletId,
            UUID sourceWalletId,
            Pageable pageable
    );

    List<RiskDecisionEntity> findByDestinationWalletIdAndSourceWalletIdNotAndIdLessThanOrderByIdDesc(
            UUID destinationWalletId,
            UUID sourceWalletId,
            Long cursor,
            Pageable pageable
    );
}