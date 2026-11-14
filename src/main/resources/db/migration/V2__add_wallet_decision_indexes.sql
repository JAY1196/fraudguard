CREATE INDEX idx_risk_decisions_source_wallet_id_id_desc
    ON risk_decisions (source_wallet_id, id DESC);

CREATE INDEX idx_risk_decisions_destination_wallet_id_id_desc
    ON risk_decisions (destination_wallet_id, id DESC);