-- V1__init_schema.sql
-- Foundational PostgreSQL 16 schema for Raqamli Inventar (TTPU)
-- All timestamps use TIMESTAMPTZ in UTC. Reference tables use soft-deactivation via 'active' boolean.

SET TIME ZONE 'UTC';

-- =========================================================================
-- 1. REFERENCE MODULE TABLES
-- =========================================================================

CREATE TABLE buildings (
    id          UUID PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    address     VARCHAR(500),
    description TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_buildings_code UNIQUE (code)
);

CREATE INDEX idx_buildings_active ON buildings (active);

CREATE TABLE floors (
    id           UUID PRIMARY KEY,
    building_id  UUID         NOT NULL,
    floor_number INTEGER      NOT NULL,
    name         VARCHAR(100) NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_floors_building FOREIGN KEY (building_id) REFERENCES buildings (id),
    CONSTRAINT uk_floors_building_number UNIQUE (building_id, floor_number)
);

CREATE INDEX idx_floors_building_id ON floors (building_id);
CREATE INDEX idx_floors_active ON floors (active);

CREATE TABLE departments (
    id          UUID PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    parent_id   UUID,
    building_id UUID,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_departments_code UNIQUE (code),
    CONSTRAINT fk_departments_parent FOREIGN KEY (parent_id) REFERENCES departments (id),
    CONSTRAINT fk_departments_building FOREIGN KEY (building_id) REFERENCES buildings (id)
);

CREATE INDEX idx_departments_parent_id ON departments (parent_id);
CREATE INDEX idx_departments_building_id ON departments (building_id);
CREATE INDEX idx_departments_active ON departments (active);

CREATE TABLE rooms (
    id            UUID PRIMARY KEY,
    floor_id      UUID         NOT NULL,
    department_id UUID,
    room_number   VARCHAR(50)  NOT NULL,
    name          VARCHAR(255),
    capacity      INTEGER,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rooms_floor FOREIGN KEY (floor_id) REFERENCES floors (id),
    CONSTRAINT fk_rooms_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT uk_rooms_floor_room_number UNIQUE (floor_id, room_number)
);

CREATE INDEX idx_rooms_floor_id ON rooms (floor_id);
CREATE INDEX idx_rooms_department_id ON rooms (department_id);
CREATE INDEX idx_rooms_active ON rooms (active);

CREATE TABLE employees (
    id            UUID PRIMARY KEY,
    employee_code VARCHAR(50)  NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    middle_name   VARCHAR(100),
    email         VARCHAR(255),
    phone         VARCHAR(50),
    position      VARCHAR(150),
    department_id UUID,
    room_id       UUID,
    hemis_id      VARCHAR(100),
    one_c_id      VARCHAR(100),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_employees_code UNIQUE (employee_code),
    CONSTRAINT uk_employees_email UNIQUE (email),
    CONSTRAINT fk_employees_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_employees_room FOREIGN KEY (room_id) REFERENCES rooms (id)
);

CREATE INDEX idx_employees_department_id ON employees (department_id);
CREATE INDEX idx_employees_room_id ON employees (room_id);
CREATE INDEX idx_employees_active ON employees (active);

CREATE TABLE categories (
    id                 UUID PRIMARY KEY,
    code               VARCHAR(50)  NOT NULL,
    name               VARCHAR(255) NOT NULL,
    parent_id          UUID,
    depreciation_years INTEGER,
    custom_fields      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_categories_code UNIQUE (code),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
);

CREATE INDEX idx_categories_parent_id ON categories (parent_id);
CREATE INDEX idx_categories_active ON categories (active);
CREATE INDEX idx_categories_custom_fields_gin ON categories USING GIN (custom_fields);

-- =========================================================================
-- 2. SECURITY & AUTH MODULE TABLES
-- =========================================================================

CREATE TABLE user_accounts (
    id                    UUID PRIMARY KEY,
    username              VARCHAR(100) NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    employee_id           UUID,
    department_id         UUID,
    building_id           UUID,
    totp_secret           VARCHAR(128),
    totp_enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at         TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_accounts_username UNIQUE (username),
    CONSTRAINT uk_user_accounts_email UNIQUE (email),
    CONSTRAINT fk_user_accounts_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_user_accounts_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_user_accounts_building FOREIGN KEY (building_id) REFERENCES buildings (id)
);

CREATE INDEX idx_user_accounts_department_id ON user_accounts (department_id);
CREATE INDEX idx_user_accounts_building_id ON user_accounts (building_id);

CREATE TABLE user_roles (
    user_id   UUID        NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, role_name),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
);

CREATE TABLE user_permissions (
    user_id    UUID         NOT NULL,
    permission VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id, permission),
    CONSTRAINT fk_user_permissions_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
);

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMPTZ,
    user_agent VARCHAR(255),
    ip_address VARCHAR(64),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

-- =========================================================================
-- 3. SPRING MODULITH EVENT PUBLICATION TABLE
-- =========================================================================

CREATE TABLE IF NOT EXISTS event_publication (
    id               UUID PRIMARY KEY,
    listener_id      TEXT        NOT NULL,
    event_type       TEXT        NOT NULL,
    serialized_event TEXT        NOT NULL,
    publication_date TIMESTAMPTZ NOT NULL,
    completion_date  TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_event_publication_by_completion_date ON event_publication (completion_date);
