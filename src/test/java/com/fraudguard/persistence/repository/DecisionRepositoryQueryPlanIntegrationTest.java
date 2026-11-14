package com.fraudguard.persistence.repository;

import com.fraudguard.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(PostgresTestcontainersConfiguration.class)
class DecisionRepositoryQueryPlanIntegrationTest {

    private static final UUID WALLET_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void walletQueriesShouldUseIndexesWithoutSortNodes() {

        jdbcTemplate.update("""
                INSERT INTO risk_decisions (
                    event_id,
                    request_hash,
                    source_wallet_id,
                    destination_wallet_id,
                    amount_paise,
                    transaction_type,
                    currency,
                    event_timestamp,
                    decision,
                    score,
                    reason_codes,
                    degraded,
                    evaluated_at
                )
                SELECT
                    md5(gs::text)::uuid,
                    repeat('a', 64),
                    CASE
                        WHEN gs <= 200 THEN ?::uuid
                        ELSE md5('source-' || gs::text)::uuid
                    END,
                    CASE
                        WHEN gs BETWEEN 201 AND 400 THEN ?::uuid
                        ELSE md5('destination-' || gs::text)::uuid
                    END,
                    100000,
                    'TRANSFER',
                    'INR',
                    now(),
                    'ALLOW',
                    10,
                    '[]'::jsonb,
                    false,
                    now()
                FROM generate_series(1, 50000) AS gs
                """,
                WALLET_ID,
                WALLET_ID
        );

        jdbcTemplate.execute("ANALYZE risk_decisions");

        String sourcePlan = String.join("\n",
                jdbcTemplate.queryForList("""
                        EXPLAIN (ANALYZE, BUFFERS)
                        SELECT id
                        FROM risk_decisions
                        WHERE source_wallet_id = ?
                        ORDER BY id DESC
                        LIMIT ?
                        """,
                        String.class,
                        WALLET_ID,
                        21
                )
        );

        String destinationPlan = String.join("\n",
                jdbcTemplate.queryForList("""
                        EXPLAIN (ANALYZE, BUFFERS)
                        SELECT id
                        FROM risk_decisions
                        WHERE destination_wallet_id = ?
                          AND source_wallet_id <> ?
                        ORDER BY id DESC
                        LIMIT ?
                        """,
                        String.class,
                        WALLET_ID,
                        WALLET_ID,
                        21
                )
        );

        System.out.println("\n===== SOURCE WALLET QUERY PLAN =====");
        System.out.println(sourcePlan);

        System.out.println("\n===== DESTINATION WALLET QUERY PLAN =====");
        System.out.println(destinationPlan);

        assertThat(sourcePlan)
                .contains("idx_risk_decisions_source_wallet_id_id_desc")
                .doesNotContain("Sort");

        assertThat(destinationPlan)
                .contains("idx_risk_decisions_destination_wallet_id_id_desc")
                .doesNotContain("Sort");
    }
}