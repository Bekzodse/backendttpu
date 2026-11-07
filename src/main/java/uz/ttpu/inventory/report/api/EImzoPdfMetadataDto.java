package uz.ttpu.inventory.report.api;

import java.time.Instant;
import java.util.UUID;

public record EImzoPdfMetadataDto(
        UUID signatureRecordId,
        String pkcs7SignatureId,
        String signerPinfl,
        String signerTin,
        String signerFullName,
        String signerOrganization,
        String certificateSerialNumber,
        String pdfSha256Hash,
        boolean verified,
        Instant signedAt,
        Instant verifiedAt
) {
}
