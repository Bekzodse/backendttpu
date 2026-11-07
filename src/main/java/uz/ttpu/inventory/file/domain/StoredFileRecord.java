package uz.ttpu.inventory.file.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.ttpu.inventory.common.domain.BaseEntity;

@Entity
@Table(name = "stored_file_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredFileRecord extends BaseEntity {

    @Column(name = "file_key", nullable = false, unique = true, length = 255)
    private String fileKey;

    @Column(name = "bucket_name", nullable = false, length = 100)
    private String bucketName;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "sha256_checksum", nullable = false, length = 64)
    private String sha256Checksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private FileCategory category;

    @Column(name = "reference_entity_id")
    private UUID referenceEntityId;

    @Column(name = "uploaded_by_username", nullable = false, length = 150)
    private String uploadedByUsername;

    @Column(name = "uploaded_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant uploadedAt;
}
