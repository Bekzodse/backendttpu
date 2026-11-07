-- =====================================================================
-- V11: E-IMZO Signed PDF Verification & Signer Details Table
-- =====================================================================

CREATE TABLE eimzo_signature_records (
    id UUID PRIMARY KEY,
    document_type VARCHAR(100) NOT NULL,
    document_id UUID NOT NULL,
    document_number VARCHAR(150),
    pdf_sha256_hash VARCHAR(64) NOT NULL,
    pdf_size_bytes BIGINT NOT NULL,
    pkcs7_signature_id VARCHAR(150) NOT NULL,
    signer_pinfl VARCHAR(32) NOT NULL,
    signer_tin VARCHAR(32),
    signer_full_name VARCHAR(255) NOT NULL,
    signer_organization VARCHAR(255),
    certificate_serial_number VARCHAR(100) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT TRUE,
    verification_message VARCHAR(500),
    signed_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_eimzo_signature_records_document ON eimzo_signature_records(document_id);
CREATE INDEX idx_eimzo_signature_records_pinfl ON eimzo_signature_records(signer_pinfl);
