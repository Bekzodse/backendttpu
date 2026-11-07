-- V4__audit_record_table.sql
-- Audit record table capturing every READ, CHANGE, and EXPORT of an item card via AOP.

SET TIME ZONE 'UTC';

CREATE TABLE audit_record (
    id             UUID         PRIMARY KEY,
    action_type    VARCHAR(32)  NOT NULL,
    target_type    VARCHAR(64)  NOT NULL,
    target_id      VARCHAR(255),
    method_name    VARCHAR(150) NOT NULL,
    actor_username VARCHAR(150) NOT NULL,
    details        TEXT,
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_record_target_id ON audit_record (target_id, occurred_at DESC);
CREATE INDEX idx_audit_record_action_type ON audit_record (action_type, occurred_at DESC);
