-- V3__receipt_module.sql
-- Receipt and ReceiptLine tables for warehouse intake (Kirim).
-- Enforces unique serial number within a category and NUMERIC(18, 2) for price without VAT.

SET TIME ZONE 'UTC';

CREATE TABLE receipts (
    id                      UUID         PRIMARY KEY,
    receipt_number          VARCHAR(100) NOT NULL,
    contract_number         VARCHAR(150) NOT NULL,
    contract_date           TIMESTAMPTZ,
    invoice_number          VARCHAR(150) NOT NULL,
    invoice_date            TIMESTAMPTZ,
    supplier_name           VARCHAR(255) NOT NULL,
    supplier_tin            VARCHAR(50),
    status                  VARCHAR(50)  NOT NULL DEFAULT 'DRAFT',
    received_at             TIMESTAMPTZ  NOT NULL,
    warehouse_building_id   UUID,
    received_by_employee_id UUID,
    notes                   TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_receipts_receipt_number UNIQUE (receipt_number),
    CONSTRAINT fk_receipts_warehouse_building FOREIGN KEY (warehouse_building_id) REFERENCES buildings (id),
    CONSTRAINT fk_receipts_received_by FOREIGN KEY (received_by_employee_id) REFERENCES employees (id)
);

CREATE INDEX idx_receipts_status ON receipts (status);
CREATE INDEX idx_receipts_contract_number ON receipts (contract_number);
CREATE INDEX idx_receipts_invoice_number ON receipts (invoice_number);

CREATE TABLE receipt_lines (
    id                UUID           PRIMARY KEY,
    receipt_id        UUID           NOT NULL,
    name              VARCHAR(255)   NOT NULL,
    category_id       UUID           NOT NULL,
    serial_number     VARCHAR(150)   NOT NULL,
    price_without_vat NUMERIC(18, 2) NOT NULL,
    quantity          INTEGER        NOT NULL DEFAULT 1,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_receipt_lines_receipt FOREIGN KEY (receipt_id) REFERENCES receipts (id) ON DELETE CASCADE,
    CONSTRAINT fk_receipt_lines_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT uk_receipt_lines_category_serial UNIQUE (category_id, serial_number)
);

CREATE INDEX idx_receipt_lines_receipt_id ON receipt_lines (receipt_id);
CREATE INDEX idx_receipt_lines_category_id ON receipt_lines (category_id);
