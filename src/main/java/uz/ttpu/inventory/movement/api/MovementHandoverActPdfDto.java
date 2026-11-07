package uz.ttpu.inventory.movement.api;

import java.time.Instant;
import java.util.UUID;

public record MovementHandoverActPdfDto(
        byte[] pdfBytes,
        String fileName,
        String formCode,
        String documentType,
        UUID operationId,
        String documentNumber,
        String sha256Checksum,
        int eimzoSignatureCount,
        String latestPkcs7SignatureId,
        Instant generatedAt
) {
}
