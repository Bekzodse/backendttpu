-- =====================================================================
-- V9: Offline Sync Package Version, Idempotent Scan Actions & Reconciliation
-- =====================================================================

ALTER TABLE inventory_sessions
    ADD COLUMN IF NOT EXISTS package_version BIGINT NOT NULL DEFAULT 1;

CREATE TABLE inventory_scan_actions (
    id UUID PRIMARY KEY,
    client_action_id UUID NOT NULL,
    session_id UUID NOT NULL REFERENCES inventory_sessions(id) ON DELETE CASCADE,
    scanned_room_id UUID NOT NULL,
    scanned_by_employee_id UUID NOT NULL,
    expected_room_id UUID,
    item_id UUID,
    inventory_number VARCHAR(100),
    qr_code VARCHAR(100),
    item_name VARCHAR(255),
    scanned_at TIMESTAMPTZ NOT NULL,
    reconciliation_category VARCHAR(64) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_inventory_scan_actions_client_action_id UNIQUE (client_action_id)
);

CREATE INDEX idx_inventory_scan_actions_session_id ON inventory_scan_actions(session_id);
CREATE INDEX idx_inventory_scan_actions_session_category ON inventory_scan_actions(session_id, reconciliation_category);
