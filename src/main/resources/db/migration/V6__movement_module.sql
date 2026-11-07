-- V6__movement_module.sql
-- Internal logistics (Issue & Transfer) insert-only movements ledger table
-- plus PostgreSQL database triggers rejecting UPDATE and DELETE on movement history tables.

SET TIME ZONE 'UTC';

CREATE TABLE movements (
    id                 UUID         PRIMARY KEY,
    operation_id       UUID         NOT NULL,
    document_number    VARCHAR(100) NOT NULL,
    movement_type      VARCHAR(50)  NOT NULL,
    event_type         VARCHAR(50)  NOT NULL,
    item_id            UUID         NOT NULL,
    from_building_id   UUID,
    to_building_id     UUID,
    from_floor_id      UUID,
    to_floor_id        UUID,
    from_room_id       UUID,
    to_room_id         UUID         NOT NULL,
    from_department_id UUID,
    to_department_id   UUID,
    from_employee_id   UUID,
    to_employee_id     UUID         NOT NULL,
    actor_employee_id  UUID,
    totp_verified      BOOLEAN      NOT NULL DEFAULT FALSE,
    reason             TEXT,
    recorded_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_movements_item FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT fk_movements_to_room FOREIGN KEY (to_room_id) REFERENCES rooms (id),
    CONSTRAINT fk_movements_to_employee FOREIGN KEY (to_employee_id) REFERENCES employees (id)
);

CREATE INDEX idx_movements_operation_id ON movements (operation_id, recorded_at ASC);
CREATE INDEX idx_movements_item_id ON movements (item_id, recorded_at DESC);

-- =========================================================================
-- POSTGRESQL TRIGGER REJECTING UPDATE AND DELETE ON MOVEMENT HISTORY TABLES
-- =========================================================================

CREATE OR REPLACE FUNCTION reject_movement_history_update_or_delete()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Table % is insert-only: % operations are strictly prohibited', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER trg_movements_insert_only
BEFORE UPDATE OR DELETE ON movements
FOR EACH ROW
EXECUTE FUNCTION reject_movement_history_update_or_delete();

CREATE TRIGGER trg_item_state_history_insert_only
BEFORE UPDATE OR DELETE ON item_state_history
FOR EACH ROW
EXECUTE FUNCTION reject_movement_history_update_or_delete();
