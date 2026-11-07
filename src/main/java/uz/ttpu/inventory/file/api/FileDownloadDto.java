package uz.ttpu.inventory.file.api;

import java.time.Instant;
import java.util.UUID;

public record FileDownloadDto(
    UUID id,
    String originalFilename,
    String contentType,
    long fileSizeBytes,
    String sha256Checksum,
    byte[] data,
    Instant uploadedAt) {}
