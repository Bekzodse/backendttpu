-- =====================================================================
-- V7: Repair Tickets, Multi-Channel Notification Logs & ShedLock Table
-- =====================================================================

ALTER TABLE items
    ADD COLUMN IF NOT EXISTS warranty_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS expected_return_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_items_warranty_expires_at ON items(warranty_expires_at);
CREATE INDEX IF NOT EXISTS idx_items_expected_return_at ON items(expected_return_at);

CREATE TABLE repair_tickets (
    id UUID PRIMARY KEY,
    ticket_number VARCHAR(100) NOT NULL UNIQUE,
    item_id UUID NOT NULL REFERENCES items(id),
    reported_by_employee_id UUID NOT NULL,
    assigned_technician_employee_id UUID,
    state VARCHAR(32) NOT NULL DEFAULT 'NEW',
    issue_description TEXT NOT NULL,
    vendor_name VARCHAR(255),
    vendor_contract_number VARCHAR(150),
    estimated_cost NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    actual_cost NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    expected_return_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    resolution_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_repair_tickets_item_id ON repair_tickets(item_id);
CREATE INDEX idx_repair_tickets_state_expected_return ON repair_tickets(state, expected_return_at);

CREATE TABLE notification_logs (
    id UUID PRIMARY KEY,
    event_type VARCHAR(150) NOT NULL,
    reference_id UUID,
    channel VARCHAR(32) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    message_body TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_logs_reference_id ON notification_logs(reference_id);
CREATE INDEX idx_notification_logs_channel_status ON notification_logs(channel, status);

CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
