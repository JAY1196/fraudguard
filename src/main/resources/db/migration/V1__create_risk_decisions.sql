CREATE TABLE risk_decisions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    event_id UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL,

    source_wallet_id UUID NOT NULL,
    destination_wallet_id UUID NOT NULL,
    amount_paise BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    event_timestamp TIMESTAMPTZ NOT NULL,

    decision VARCHAR(10),
    score SMALLINT,
    reason_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    degraded BOOLEAN NOT NULL DEFAULT FALSE,
    evaluated_at TIMESTAMPTZ,

    CONSTRAINT uq_risk_decisions_event_id
        UNIQUE (event_id),

    CONSTRAINT ck_risk_decisions_request_hash
        CHECK (request_hash ~ '^[0-9a-f]{64}$'),

    CONSTRAINT ck_risk_decisions_amount
        CHECK (amount_paise > 0),

    CONSTRAINT ck_risk_decisions_type
        CHECK (transaction_type IN ('TOPUP', 'TRANSFER', 'WITHDRAWAL', 'REVERSAL')),

    CONSTRAINT ck_risk_decisions_currency
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT ck_risk_decisions_decision
        CHECK (decision IS NULL OR decision IN ('ALLOW', 'REVIEW', 'BLOCK')),

    CONSTRAINT ck_risk_decisions_score
        CHECK (score IS NULL OR score BETWEEN 0 AND 100),

    CONSTRAINT ck_risk_decisions_reason_codes
        CHECK (jsonb_typeof(reason_codes) = 'array'),

    CONSTRAINT ck_risk_decisions_completion
        CHECK (
            (decision IS NULL AND score IS NULL AND evaluated_at IS NULL)
            OR
            (decision IS NOT NULL AND score IS NOT NULL AND evaluated_at IS NOT NULL)
        )
);

COMMENT ON TABLE risk_decisions IS
    'Durable fraud risk evaluation decisions and event idempotency records';

COMMENT ON COLUMN risk_decisions.request_hash IS
    'Lowercase SHA-256 hash of the normalized evaluation request';

COMMENT ON COLUMN risk_decisions.id IS
    'Monotonically generated identifier used for keyset pagination';

CREATE OR REPLACE FUNCTION enforce_risk_decision_completion()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM risk_decisions
        WHERE id = NEW.id
          AND (
              decision IS NULL
              OR score IS NULL
              OR evaluated_at IS NULL
          )
    ) THEN
        RAISE EXCEPTION
            'Risk decision % cannot be committed incomplete', NEW.event_id;
    END IF;

    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_risk_decision_completion
AFTER INSERT OR UPDATE ON risk_decisions
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION enforce_risk_decision_completion();