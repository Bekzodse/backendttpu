-- =====================================================================
-- V8: Inventory Sessions, Per-Room Progress & Expected Item Lists
-- =====================================================================

CREATE TABLE inventory_sessions (
    id UUID PRIMARY KEY,
    session_code VARCHAR(100) NOT NULL UNIQUE,
    rector_order_number VARCHAR(150) NOT NULL,
    rector_order_date TIMESTAMPTZ NOT NULL,
    session_date TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    chairman_employee_id UUID NOT NULL,
    commission_member_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    room_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inventory_sessions_status ON inventory_sessions(status);
CREATE INDEX idx_inventory_sessions_session_date ON inventory_sessions(session_date);

CREATE TABLE inventory_session_rooms (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES inventory_sessions(id) ON DELETE CASCADE,
    room_id UUID NOT NULL,
    expected_count INTEGER NOT NULL DEFAULT 0,
    found_count INTEGER NOT NULL DEFAULT 0,
    not_found_count INTEGER NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_inventory_session_rooms_session_room UNIQUE (session_id, room_id)
);

CREATE INDEX idx_inventory_session_rooms_session_id ON inventory_session_rooms(session_id);
CREATE INDEX idx_inventory_session_rooms_room_id ON inventory_session_rooms(room_id);

CREATE TABLE inventory_expected_items (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES inventory_sessions(id) ON DELETE CASCADE,
    room_id UUID NOT NULL,
    item_id UUID NOT NULL REFERENCES items(id),
    inventory_number VARCHAR(100) NOT NULL,
    qr_code VARCHAR(32) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    custodian_employee_id UUID,
    book_value NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    line_status VARCHAR(32) NOT NULL DEFAULT 'EXPECTED',
    scanned_room_id UUID,
    scanned_by_employee_id UUID,
    scanned_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_inventory_expected_items_session_item UNIQUE (session_id, item_id)
);

CREATE INDEX idx_inventory_expected_items_session_room ON inventory_expected_items(session_id, room_id);
CREATE INDEX idx_inventory_expected_items_session_qr ON inventory_expected_items(session_id, qr_code);
