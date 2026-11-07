-- V2__envers_and_assets.sql
-- 1. Hibernate Envers audit tables for Reference Data
-- 2. Asset module tables (items, item_state_history)

SET TIME ZONE 'UTC';

-- =========================================================================
-- 1. HIBERNATE ENVERS REVISION & REFERENCE AUDIT TABLES
-- =========================================================================

CREATE SEQUENCE IF NOT EXISTS revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE revinfo (
    rev      INTEGER PRIMARY KEY DEFAULT nextval('revinfo_seq'),
    revtstmp BIGINT
);

CREATE TABLE buildings_aud (
    id          UUID    NOT NULL,
    rev         INTEGER NOT NULL,
    revtype     SMALLINT,
    code        VARCHAR(50),
    name        VARCHAR(255),
    address     VARCHAR(500),
    description TEXT,
    active      BOOLEAN,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_buildings_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE floors_aud (
    id           UUID    NOT NULL,
    rev          INTEGER NOT NULL,
    revtype      SMALLINT,
    building_id  UUID,
    floor_number INTEGER,
    name         VARCHAR(100),
    active       BOOLEAN,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_floors_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE departments_aud (
    id          UUID    NOT NULL,
    rev         INTEGER NOT NULL,
    revtype     SMALLINT,
    code        VARCHAR(50),
    name        VARCHAR(255),
    parent_id   UUID,
    building_id UUID,
    active      BOOLEAN,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_departments_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE rooms_aud (
    id            UUID    NOT NULL,
    rev           INTEGER NOT NULL,
    revtype       SMALLINT,
    floor_id      UUID,
    department_id UUID,
    room_number   VARCHAR(50),
    name          VARCHAR(255),
    capacity      INTEGER,
    active        BOOLEAN,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_rooms_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE employees_aud (
    id            UUID    NOT NULL,
    rev           INTEGER NOT NULL,
    revtype       SMALLINT,
    employee_code VARCHAR(50),
    first_name    VARCHAR(100),
    last_name     VARCHAR(100),
    middle_name   VARCHAR(100),
    email         VARCHAR(255),
    phone         VARCHAR(50),
    position      VARCHAR(150),
    department_id UUID,
    room_id       UUID,
    hemis_id      VARCHAR(100),
    one_c_id      VARCHAR(100),
    active        BOOLEAN,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_employees_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

CREATE TABLE categories_aud (
    id                 UUID    NOT NULL,
    rev                INTEGER NOT NULL,
    revtype            SMALLINT,
    code               VARCHAR(50),
    name               VARCHAR(255),
    parent_id          UUID,
    depreciation_years INTEGER,
    custom_fields      JSONB,
    active             BOOLEAN,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_categories_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);

-- =========================================================================
-- 2. ASSET MODULE TABLES
-- =========================================================================

CREATE TABLE items (
    id                    UUID           PRIMARY KEY,
    inventory_number      VARCHAR(100)   NOT NULL,
    serial_number         VARCHAR(150),
    qr_code               VARCHAR(255),
    name                  VARCHAR(255)   NOT NULL,
    description           TEXT,
    category_id           UUID           NOT NULL,
    state                 VARCHAR(50)    NOT NULL DEFAULT 'OMBORDA',
    purchase_price        NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    residual_value        NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    commissioned_at       TIMESTAMPTZ,
    building_id           UUID,
    floor_id              UUID,
    room_id               UUID,
    department_id         UUID,
    custodian_employee_id UUID,
    custom_attributes     JSONB          NOT NULL DEFAULT '{}'::jsonb,
    one_c_ref_id          VARCHAR(100),
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_items_inventory_number UNIQUE (inventory_number),
    CONSTRAINT uk_items_qr_code UNIQUE (qr_code),
    CONSTRAINT fk_items_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_items_building FOREIGN KEY (building_id) REFERENCES buildings (id),
    CONSTRAINT fk_items_floor FOREIGN KEY (floor_id) REFERENCES floors (id),
    CONSTRAINT fk_items_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT fk_items_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_items_custodian FOREIGN KEY (custodian_employee_id) REFERENCES employees (id)
);

CREATE INDEX idx_items_state ON items (state);
CREATE INDEX idx_items_category_id ON items (category_id);
CREATE INDEX idx_items_department_id ON items (department_id);
CREATE INDEX idx_items_building_id ON items (building_id);
CREATE INDEX idx_items_custodian_id ON items (custodian_employee_id);
CREATE INDEX idx_items_custom_attributes_gin ON items USING GIN (custom_attributes);

CREATE TABLE item_state_history (
    id              UUID         PRIMARY KEY,
    item_id         UUID         NOT NULL,
    from_state      VARCHAR(50)  NOT NULL,
    to_state        VARCHAR(50)  NOT NULL,
    event           VARCHAR(50)  NOT NULL,
    document_type   VARCHAR(100) NOT NULL,
    document_ref    VARCHAR(255) NOT NULL,
    transitioned_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_item_state_history_item FOREIGN KEY (item_id) REFERENCES items (id) ON DELETE CASCADE
);

CREATE INDEX idx_item_state_history_item_id ON item_state_history (item_id, transitioned_at DESC);
