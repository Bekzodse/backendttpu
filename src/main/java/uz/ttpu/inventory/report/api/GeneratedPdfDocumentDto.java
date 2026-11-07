package uz.ttpu.inventory.report.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GeneratedPdfDocumentDto(
        byte[] pdfBytes,
        String fileName,
        String formCode,
        String documentType,
        UUID documentId,
        String documentNumber,
        String sha256Checksum,
        List<EImzoPdfMetadataDto> eimzoSignatures,
        Instant generatedAt
) {
}
