-- V18__reclaimed_parts_and_item_parent.sql
-- 1. Link child/reclaimed items to their parent written-off asset
-- 2. Audit and track usable parts reclaimed into warehouse stock during or after write-off

SET TIME ZONE 'UTC';

-- Add parent_item_id column to items table
ALTER TABLE items ADD COLUMN IF NOT EXISTS parent_item_id UUID REFERENCES items(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_items_parent_item_id ON items(parent_item_id);

-- Create write_off_reclaimed_parts tracking table
CREATE TABLE IF NOT EXISTS write_off_reclaimed_parts (
    id UUID PRIMARY KEY,
    written_off_item_id UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    reclaimed_item_id UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    part_name VARCHAR(255) NOT NULL,
    serial_number VARCHAR(150),
    inventory_number VARCHAR(100) NOT NULL,
    estimated_value NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    warehouse_building_id UUID REFERENCES buildings(id) ON DELETE SET NULL,
    warehouse_room_id UUID REFERENCES rooms(id) ON DELETE SET NULL,
    reclaimed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reclaimed_by_username VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reclaimed_parts_written_off ON write_off_reclaimed_parts(written_off_item_id);
CREATE INDEX IF NOT EXISTS idx_reclaimed_parts_reclaimed_item ON write_off_reclaimed_parts(reclaimed_item_id);
