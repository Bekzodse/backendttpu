-- V16__complex_business_rules_and_device_tracking.sql
-- Prompt 12: B3.3 Offboarding, B3.5 Repair 50% Rule, B5.6 Data Wipe, B3.4 Off-Campus Permits
-- Prompt 13: NFT-3.8 Device Revocation

ALTER TABLE repair_tickets
    ADD COLUMN IF NOT EXISTS replacement_recommended BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS replacement_recommendation_reason TEXT;

ALTER TABLE items
    ADD COLUMN IF NOT EXISTS replacement_recommended BOOLEAN NOT NULL DEFAULT FALSE;

-- 1. Clearance Checklists (Aylanma varaqa - B3.3)
CREATE TABLE IF NOT EXISTS clearance_checklists (
    id                      UUID PRIMARY KEY,
    checklist_number        VARCHAR(100) NOT NULL UNIQUE,
    employee_id             UUID NOT NULL,
    employee_code           VARCHAR(50),
    employee_full_name      VARCHAR(255),
    department_name         VARCHAR(255),
    status                  VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    dismissal_order_number  VARCHAR(100),
    dismissal_reason        TEXT,
    total_assigned_items    INT NOT NULL DEFAULT 0,
    remaining_items_count   INT NOT NULL DEFAULT 0,
    opened_at               TIMESTAMPTZ NOT NULL,
    closed_at               TIMESTAMPTZ,
    closed_by_employee_id   UUID,
    notes                   TEXT,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_clearance_checklists_employee_id ON clearance_checklists(employee_id);
CREATE INDEX IF NOT EXISTS idx_clearance_checklists_status ON clearance_checklists(status);

-- 2. Data Wipe Certification Records (B5.6 Write-Off for IT Devices)
CREATE TABLE IF NOT EXISTS data_wipe_records (
    id                      UUID PRIMARY KEY,
    item_id                 UUID NOT NULL,
    performed_by_name       VARCHAR(255) NOT NULL,
    performed_by_employee_id UUID,
    wipe_method             VARCHAR(100) NOT NULL,
    certificate_number      VARCHAR(100),
    notes                   TEXT,
    wiped_at                TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_data_wipe_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_data_wipe_item_id ON data_wipe_records(item_id);

-- 3. Off-Campus Temporary Permits & Security Checkpoint (B3.4)
CREATE TABLE IF NOT EXISTS off_campus_permits (
    id                      UUID PRIMARY KEY,
    permit_number           VARCHAR(100) NOT NULL UNIQUE,
    item_id                 UUID NOT NULL,
    qr_code                 VARCHAR(255) NOT NULL,
    inventory_number        VARCHAR(100) NOT NULL,
    item_name               VARCHAR(255) NOT NULL,
    employee_id             UUID NOT NULL,
    employee_full_name      VARCHAR(255) NOT NULL,
    purpose                 TEXT NOT NULL,
    valid_from              TIMESTAMPTZ NOT NULL,
    valid_until             TIMESTAMPTZ NOT NULL,
    status                  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    approved_by             VARCHAR(255),
    revoked_at              TIMESTAMPTZ,
    revocation_reason       TEXT,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_permits_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_off_campus_permits_qr_code ON off_campus_permits(qr_code);
CREATE INDEX IF NOT EXISTS idx_off_campus_permits_status ON off_campus_permits(status);
CREATE INDEX IF NOT EXISTS idx_off_campus_permits_item_id ON off_campus_permits(item_id);

-- 4. Mobile User Devices & Device Revocation (NFT-3.8)
CREATE TABLE IF NOT EXISTS user_devices (
    id                      UUID PRIMARY KEY,
    user_id                 UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    device_id               VARCHAR(120) NOT NULL,
    device_name             VARCHAR(255),
    device_type             VARCHAR(50),
    os_version              VARCHAR(50),
    app_version             VARCHAR(50),
    ip_address              VARCHAR(100),
    user_agent              VARCHAR(255),
    revoked                 BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at              TIMESTAMPTZ,
    revocation_reason       TEXT,
    last_login_at           TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_user_devices_user_device UNIQUE (user_id, device_id)
);

CREATE INDEX IF NOT EXISTS idx_user_devices_device_id ON user_devices(device_id);
CREATE INDEX IF NOT EXISTS idx_user_devices_revoked ON user_devices(device_id, revoked);
