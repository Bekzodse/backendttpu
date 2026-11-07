package uz.ttpu.inventory.integration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.ttpu.inventory.common.domain.BaseEntity;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "eimzo_signature_records")
public class EImzoSignatureRecord extends BaseEntity {

    @Column(name = "document_type", nullable = false, length = 100)
    private String documentType;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "document_number", length = 150)
    private String documentNumber;

    @Column(name = "pdf_sha256_hash", nullable = false, length = 64)
    private String pdfSha256Hash;

    @Column(name = "pdf_size_bytes", nullable = false)
    private long pdfSizeBytes;

    @Column(name = "pkcs7_signature_id", nullable = false, length = 150)
    private String pkcs7SignatureId;

    @Column(name = "signer_pinfl", nullable = false, length = 32)
    private String signerPinfl;

    @Column(name = "signer_tin", length = 32)
    private String signerTin;

    @Column(name = "signer_full_name", nullable = false, length = 255)
    private String signerFullName;

    @Column(name = "signer_organization", length = 255)
    private String signerOrganization;

    @Column(name = "certificate_serial_number", nullable = false, length = 100)
    private String certificateSerialNumber;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "verification_message", length = 500)
    private String verificationMessage;

    @Column(name = "signed_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant signedAt;

    @Column(name = "verified_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant verifiedAt;
}
