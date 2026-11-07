-- V13: Asset Requisition Requests (`request` module) & Stored File Metadata (`file` module)

CREATE TABLE IF NOT EXISTS asset_requests (
    id                      UUID PRIMARY KEY,
    request_number          VARCHAR(100) NOT NULL UNIQUE,
    category_id             UUID NOT NULL,
    requested_item_name     VARCHAR(255) NOT NULL,
    quantity                INTEGER NOT NULL,
    estimated_unit_cost     NUMERIC(18, 2) NOT NULL,
    total_estimated_cost    NUMERIC(18, 2) NOT NULL,
    department_id           UUID NOT NULL,
    building_id             UUID,
    requester_employee_id   UUID NOT NULL,
    justification           VARCHAR(1000),
    status                  VARCHAR(40) NOT NULL,
    fulfilled_item_id       UUID,
    submitted_at            TIMESTAMPTZ,
    resolved_at             TIMESTAMPTZ,
    resolved_by_username    VARCHAR(150),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_asset_requests_department_id
    ON asset_requests (department_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_asset_requests_status
    ON asset_requests (status, created_at DESC);

CREATE TABLE IF NOT EXISTS stored_file_records (
    id                      UUID PRIMARY KEY,
    file_key                VARCHAR(255) NOT NULL UNIQUE,
    bucket_name             VARCHAR(100) NOT NULL,
    original_filename       VARCHAR(255) NOT NULL,
    content_type            VARCHAR(150) NOT NULL,
    file_size_bytes         BIGINT NOT NULL,
    sha256_checksum         VARCHAR(64) NOT NULL,
    category                VARCHAR(50) NOT NULL,
    reference_entity_id     UUID,
    uploaded_by_username    VARCHAR(150) NOT NULL,
    uploaded_at             TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_stored_file_records_reference_id
    ON stored_file_records (reference_entity_id, uploaded_at DESC);
