package uz.ttpu.inventory.file.api;

import java.time.Instant;
import java.util.UUID;
import uz.ttpu.inventory.file.domain.FileCategory;

public record StoredFileDto(
        UUID id,
        String fileKey,
        String bucketName,
        String originalFilename,
        String contentType,
        long fileSizeBytes,
        String sha256Checksum,
        FileCategory category,
        UUID referenceEntityId,
        String uploadedByUsername,
        Instant uploadedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
