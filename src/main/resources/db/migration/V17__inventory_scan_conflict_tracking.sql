-- =====================================================================
-- V17: Offline Inventory Scan Conflict Tracking
-- =====================================================================

ALTER TABLE inventory_scan_actions
    ADD COLUMN IF NOT EXISTS conflict BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS conflict_details TEXT;

CREATE INDEX IF NOT EXISTS idx_inventory_scan_actions_session_conflict
    ON inventory_scan_actions(session_id, conflict);
