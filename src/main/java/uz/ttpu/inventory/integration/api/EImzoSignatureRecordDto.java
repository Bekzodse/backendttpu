package uz.ttpu.inventory.integration.api;

import java.time.Instant;
import java.util.UUID;

public record EImzoSignatureRecordDto(
        UUID id,
        String documentType,
        UUID documentId,
        String documentNumber,
        String pdfSha256Hash,
        long pdfSizeBytes,
        String pkcs7SignatureId,
        String signerPinfl,
        String signerTin,
        String signerFullName,
        String signerOrganization,
        String certificateSerialNumber,
        boolean verified,
        String verificationMessage,
        Instant signedAt,
        Instant verifiedAt,
        Instant createdAt
) {
}
