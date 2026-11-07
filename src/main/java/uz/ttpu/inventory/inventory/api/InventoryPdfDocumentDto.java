package uz.ttpu.inventory.inventory.api;

import java.time.Instant;
import java.util.UUID;

public record InventoryPdfDocumentDto(
    byte[] pdfBytes,
    String fileName,
    String formCode,
    String documentType,
    UUID sessionId,
    String sessionCode,
    String sha256Checksum,
    int eimzoSignatureCount,
    String latestPkcs7SignatureId,
    Instant generatedAt) {}
