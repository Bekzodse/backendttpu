-- V15__pg_trgm_fuzzy_search_optimization.sql
-- Prompt 9: Full-Text Fuzzy Search Optimization (FT-8.2, FT-8.3)
-- 1. Enable pg_trgm extension in PostgreSQL
-- 2. Add model, responsible_person, and room searchable columns & synchronization triggers on items
-- 3. Add GIN (gin_trgm_ops) trigram indexes on name, inventory_number, serial_number, model, responsible_person, and room in items and related tables

SET TIME ZONE 'UTC';

-- 1. Enable pg_trgm extension
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 2. Add indexed search columns to items for sub-3-second 200k-row trigram search
ALTER TABLE items
    ADD COLUMN IF NOT EXISTS model              VARCHAR(150),
    ADD COLUMN IF NOT EXISTS responsible_person VARCHAR(255),
    ADD COLUMN IF NOT EXISTS room               VARCHAR(255);

-- Backfill existing rows from custom_attributes, employees, and rooms
UPDATE items i
SET model = COALESCE(i.model, i.custom_attributes ->> 'model')
WHERE i.model IS NULL AND (i.custom_attributes ? 'model');

UPDATE items i
SET responsible_person = TRIM(e.first_name || ' ' || e.last_name || COALESCE(' ' || e.middle_name, '') || ' (' || e.employee_code || ')')
FROM employees e
WHERE i.custodian_employee_id = e.id
  AND (i.responsible_person IS NULL OR i.responsible_person = '');

UPDATE items i
SET room = TRIM(r.room_number || COALESCE(' ' || r.name, ''))
FROM rooms r
WHERE i.room_id = r.id
  AND (i.room IS NULL OR i.room = '');

-- Trigger function to automatically sync model, responsible_person, and room on items INSERT/UPDATE
CREATE OR REPLACE FUNCTION fn_sync_item_search_fields()
RETURNS TRIGGER AS $$
BEGIN
    IF (NEW.model IS NULL OR NEW.model = '') AND NEW.custom_attributes IS NOT NULL AND (NEW.custom_attributes ? 'model') THEN
        NEW.model := NEW.custom_attributes ->> 'model';
    END IF;

    IF NEW.custodian_employee_id IS NOT NULL AND (NEW.responsible_person IS NULL OR NEW.responsible_person = '' OR TG_OP = 'UPDATE' AND NEW.custodian_employee_id IS DISTINCT FROM OLD.custodian_employee_id) THEN
        SELECT TRIM(e.first_name || ' ' || e.last_name || COALESCE(' ' || e.middle_name, '') || ' (' || e.employee_code || ')')
        INTO NEW.responsible_person
        FROM employees e
        WHERE e.id = NEW.custodian_employee_id;
    END IF;

    IF NEW.room_id IS NOT NULL AND (NEW.room IS NULL OR NEW.room = '' OR TG_OP = 'UPDATE' AND NEW.room_id IS DISTINCT FROM OLD.room_id) THEN
        SELECT TRIM(r.room_number || COALESCE(' ' || r.name, ''))
        INTO NEW.room
        FROM rooms r
        WHERE r.id = NEW.room_id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_items_sync_search_fields ON items;
CREATE TRIGGER trg_items_sync_search_fields
BEFORE INSERT OR UPDATE ON items
FOR EACH ROW
EXECUTE FUNCTION fn_sync_item_search_fields();

-- Trigger functions to propagate employee/room label updates to items
CREATE OR REPLACE FUNCTION fn_propagate_employee_search_label()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE items
    SET responsible_person = TRIM(NEW.first_name || ' ' || NEW.last_name || COALESCE(' ' || NEW.middle_name, '') || ' (' || NEW.employee_code || ')')
    WHERE custodian_employee_id = NEW.id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_employees_sync_item_search ON employees;
CREATE TRIGGER trg_employees_sync_item_search
AFTER UPDATE OF first_name, last_name, middle_name, employee_code ON employees
FOR EACH ROW
EXECUTE FUNCTION fn_propagate_employee_search_label();

CREATE OR REPLACE FUNCTION fn_propagate_room_search_label()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE items
    SET room = TRIM(NEW.room_number || COALESCE(' ' || NEW.name, ''))
    WHERE room_id = NEW.id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_rooms_sync_item_search ON rooms;
CREATE TRIGGER trg_rooms_sync_item_search
AFTER UPDATE OF room_number, name ON rooms
FOR EACH ROW
EXECUTE FUNCTION fn_propagate_room_search_label();

-- 3. Create GIN trigram (gin_trgm_ops) indexes on items and related tables (employees, rooms)
CREATE INDEX IF NOT EXISTS idx_items_name_trgm
    ON items USING GIN (name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_items_inventory_number_trgm
    ON items USING GIN (inventory_number gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_items_serial_number_trgm
    ON items USING GIN (serial_number gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_items_model_trgm
    ON items USING GIN (model gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_items_responsible_person_trgm
    ON items USING GIN (responsible_person gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_items_room_trgm
    ON items USING GIN (room gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_employees_first_name_trgm
    ON employees USING GIN (first_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_employees_last_name_trgm
    ON employees USING GIN (last_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_employees_code_trgm
    ON employees USING GIN (employee_code gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_rooms_room_number_trgm
    ON rooms USING GIN (room_number gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_rooms_name_trgm
    ON rooms USING GIN (name gin_trgm_ops);
