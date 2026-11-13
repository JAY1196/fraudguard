package com.fraudguard.persistence.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DecisionClaimRepository {

    private final JdbcTemplate jdbcTemplate;

    public DecisionClaimRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Long> claim(
            UUID eventId,
            String requestHash,
            UUID sourceWalletId,
            UUID destinationWalletId,
            long amountPaise,
            String transactionType,
            String currency,
            java.time.Instant eventTimestamp
    ) {
        String sql = """
                INSERT INTO risk_decisions (
                    event_id,
                    request_hash,
                    source_wallet_id,
                    destination_wallet_id,
                    amount_paise,
                    transaction_type,
                    currency,
                    event_timestamp
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                RETURNING id
                """;

        List<Long> ids = jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> resultSet.getLong("id"),
                eventId,
                requestHash,
                sourceWalletId,
                destinationWalletId,
                amountPaise,
                transactionType,
                currency,
                Timestamp.from(eventTimestamp)
        );

        return ids.stream().findFirst();
    }
}