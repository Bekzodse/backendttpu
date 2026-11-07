-- V5__workflow_module.sql
-- Generic Workflow Approval Engine tables (workflow_definitions, workflow_step_definitions,
-- workflow_instances, workflow_step_decisions).

SET TIME ZONE 'UTC';

CREATE TABLE workflow_definitions (
    id            UUID         PRIMARY KEY,
    code          VARCHAR(100) NOT NULL,
    name          VARCHAR(255) NOT NULL,
    document_type VARCHAR(50)  NOT NULL,
    description   TEXT,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_workflow_definitions_code UNIQUE (code)
);

CREATE INDEX idx_workflow_definitions_doc_type ON workflow_definitions (document_type, active);

CREATE TABLE workflow_step_definitions (
    id                           UUID           PRIMARY KEY,
    definition_id                UUID           NOT NULL,
    step_order                   INTEGER        NOT NULL,
    step_name                    VARCHAR(150)   NOT NULL,
    approver_role                VARCHAR(64)    NOT NULL,
    required_verification_method VARCHAR(32)    NOT NULL DEFAULT 'EITHER',
    min_amount_condition         NUMERIC(18, 2),
    max_amount_condition         NUMERIC(18, 2),
    condition_attributes         JSONB          NOT NULL DEFAULT '{}'::jsonb,
    created_at                   TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_workflow_step_def_definition FOREIGN KEY (definition_id) REFERENCES workflow_definitions (id) ON DELETE CASCADE,
    CONSTRAINT uk_workflow_step_def_order UNIQUE (definition_id, step_order)
);

CREATE INDEX idx_workflow_step_def_definition_id ON workflow_step_definitions (definition_id, step_order ASC);

CREATE TABLE workflow_instances (
    id                   UUID           PRIMARY KEY,
    definition_id        UUID           NOT NULL,
    document_type        VARCHAR(50)    NOT NULL,
    document_id          UUID           NOT NULL,
    document_number      VARCHAR(100),
    amount               NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    context_attributes   JSONB          NOT NULL DEFAULT '{}'::jsonb,
    state                VARCHAR(50)    NOT NULL DEFAULT 'DRAFT',
    current_step_order   INTEGER,
    initiated_by_user_id UUID,
    submitted_at         TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    rejection_reason     TEXT,
    rework_reason        TEXT,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_workflow_instances_definition FOREIGN KEY (definition_id) REFERENCES workflow_definitions (id)
);

CREATE INDEX idx_workflow_instances_document ON workflow_instances (document_type, document_id);
CREATE INDEX idx_workflow_instances_state ON workflow_instances (state);

CREATE TABLE workflow_step_decisions (
    id                  UUID         PRIMARY KEY,
    instance_id         UUID         NOT NULL,
    step_order          INTEGER      NOT NULL,
    step_name           VARCHAR(150) NOT NULL,
    approver_role       VARCHAR(64)  NOT NULL,
    decision            VARCHAR(32)  NOT NULL,
    verification_method VARCHAR(32),
    signature_payload   TEXT,
    reason              TEXT,
    decided_by_user_id  UUID,
    decided_by_username VARCHAR(150),
    decided_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_workflow_step_decisions_instance FOREIGN KEY (instance_id) REFERENCES workflow_instances (id) ON DELETE CASCADE
);

CREATE INDEX idx_workflow_step_decisions_instance_id ON workflow_step_decisions (instance_id, decided_at ASC);
