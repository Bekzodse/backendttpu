-- =====================================================================
-- V12: Straight-Line Depreciation Records & SXSSF Report Export Logs
-- =====================================================================

CREATE TABLE depreciation_records (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL REFERENCES items(id),
    inventory_number VARCHAR(100) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    initial_value NUMERIC(18, 2) NOT NULL,
    salvage_value NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    useful_life_years INTEGER NOT NULL,
    depreciable_base NUMERIC(18, 2) NOT NULL,
    annual_depreciation NUMERIC(18, 2) NOT NULL,
    monthly_depreciation NUMERIC(18, 2) NOT NULL,
    accumulated_depreciation NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    net_book_value NUMERIC(18, 2) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_depreciation_records_item_id ON depreciation_records(item_id);

CREATE TABLE report_export_logs (
    id UUID PRIMARY KEY,
    report_code VARCHAR(100) NOT NULL,
    report_type VARCHAR(64) NOT NULL,
    row_count INTEGER NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    generation_time_ms BIGINT NOT NULL,
    generated_by_username VARCHAR(150) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_report_export_logs_generated_at ON report_export_logs(generated_at);
