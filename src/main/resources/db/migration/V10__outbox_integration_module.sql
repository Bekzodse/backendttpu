-- =====================================================================
-- V10: Transactional Outbox Table for External Systems (1C, HEMIS, E-IMZO)
-- =====================================================================

CREATE TABLE outbox (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(150) NOT NULL UNIQUE,
    target_system VARCHAR(32) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    endpoint_url VARCHAR(500) NOT NULL,
    http_method VARCHAR(16) NOT NULL DEFAULT 'POST',
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    last_error TEXT,
    response_status_code INTEGER,
    response_body TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_status_next_attempt ON outbox(status, next_attempt_at);
CREATE INDEX idx_outbox_target_system ON outbox(target_system);
CREATE INDEX idx_outbox_aggregate ON outbox(aggregate_type, aggregate_id);
