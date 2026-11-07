package uz.ttpu.inventory.file.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.file.api.FileDownloadDto;
import uz.ttpu.inventory.file.api.FileStorageApi;
import uz.ttpu.inventory.file.api.StoredFileDto;
import uz.ttpu.inventory.file.api.UploadFileRequest;
import uz.ttpu.inventory.file.domain.FileCategory;
import uz.ttpu.inventory.file.domain.StoredFileRecord;
import uz.ttpu.inventory.file.repository.StoredFileRecordRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService implements FileStorageApi {

    public static final String DEFAULT_BUCKET_NAME = "ttpu-inventory";

    private static final Set<String> ALLOWED_PHOTO_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/jpg",
            "image/png"
    );

    private final StoredFileRecordRepository storedFileRecordRepository;
    private final StoredFileMapper storedFileMapper;
    private final MinioProperties minioProperties;
    private final S3Client s3Client;

    @Override
    @Transactional
    public StoredFileDto storeFile(UploadFileRequest request, String uploadedByUsername) {
        if (request == null) {
            throw new IllegalArgumentException("Upload file request must not be null");
        }
        byte[] contentBytes;
        try {
            contentBytes = Base64.getDecoder().decode(request.contentBase64().trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("contentBase64 is not valid Base64 data", ex);
        }

        return storeRawBytes(
                request.originalFilename(),
                request.contentType(),
                request.category(),
                contentBytes,
                request.referenceEntityId(),
                uploadedByUsername
        );
    }

    @Override
    @Transactional
    public StoredFileDto storeRawBytes(
            String filename,
            String contentType,
            FileCategory category,
            byte[] bytes,
            UUID referenceEntityId,
            String uploadedByUsername
    ) {
        validateFile(filename, contentType, category, bytes);

        String bucket = (minioProperties != null && minioProperties.getBucketName() != null)
                ? minioProperties.getBucketName()
                : DEFAULT_BUCKET_NAME;

        String sha256 = computeSha256Hex(bytes);
        Instant now = Instant.now();
        String sanitizedFilename = sanitizeFilename(filename);
        String fileKey = category.name().toLowerCase(Locale.ROOT)
                + "/" + UUID.randomUUID()
                + "-" + sanitizedFilename;

        // Persist object to MinIO S3
        uploadToMinio(bucket, fileKey, contentType, bytes);

        StoredFileRecord record = StoredFileRecord.builder()
                .fileKey(fileKey)
                .bucketName(bucket)
                .originalFilename(filename.trim())
                .contentType(contentType.trim().toLowerCase(Locale.ROOT))
                .fileSizeBytes(bytes.length)
                .sha256Checksum(sha256)
                .category(category)
                .referenceEntityId(referenceEntityId)
                .uploadedByUsername(
                        uploadedByUsername != null && !uploadedByUsername.isBlank()
                                ? uploadedByUsername.trim()
                                : "SYSTEM"
                )
                .uploadedAt(now)
                .build();

        return storedFileMapper.toDto(storedFileRecordRepository.saveAndFlush(record));
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadDto downloadFile(UUID id) {
        StoredFileRecord record = storedFileRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StoredFileRecord", id));

        byte[] fileBytes = fetchFromMinio(record.getBucketName(), record.getFileKey());
        if (fileBytes != null && fileBytes.length > 0) {
            String verifiedChecksum = computeSha256Hex(fileBytes);
            if (!verifiedChecksum.equalsIgnoreCase(record.getSha256Checksum())) {
                log.warn("Checksum mismatch for fileId {}: expected={}, actual={}",
                        id, record.getSha256Checksum(), verifiedChecksum);
            }
        } else {
            fileBytes = new byte[0];
        }

        return new FileDownloadDto(
                record.getId(),
                record.getOriginalFilename(),
                record.getContentType(),
                record.getFileSizeBytes(),
                record.getSha256Checksum(),
                fileBytes,
                record.getUploadedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public StoredFileDto getMetadata(UUID id) {
        StoredFileRecord entity = storedFileRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StoredFileRecord", id));
        return storedFileMapper.toDto(entity);
    }

    @Transactional(readOnly = true)
    public StoredFileDto getById(UUID id) {
        return getMetadata(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredFileDto> listByReferenceEntity(UUID referenceEntityId) {
        return storedFileRecordRepository.findAllByReferenceEntityIdOrderByUploadedAtDesc(referenceEntityId)
                .stream()
                .map(storedFileMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredFileDto> listAll() {
        return storedFileRecordRepository.findAll()
                .stream()
                .map(storedFileMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public void deleteFile(UUID id, String actorUsername) {
        StoredFileRecord record = storedFileRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StoredFileRecord", id));

        deleteFromMinio(record.getBucketName(), record.getFileKey());
        storedFileRecordRepository.delete(record);
        log.info("File {} ({}) deleted by {}", id, record.getFileKey(), actorUsername);
    }

    private void validateFile(String filename, String contentType, FileCategory category, byte[] bytes) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Original filename must not be blank");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Content type must not be blank");
        }
        if (category == null) {
            throw new IllegalArgumentException("File category must be specified");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Uploaded file content must not be empty");
        }

        String normalizedType = contentType.trim().toLowerCase(Locale.ROOT);
        boolean isPhoto = (category == FileCategory.ITEM_PHOTO || category == FileCategory.REPAIR_PHOTO);

        if (isPhoto) {
            if (!ALLOWED_PHOTO_TYPES.contains(normalizedType)) {
                throw new IllegalArgumentException(
                        "File content-type '" + contentType + "' is not permitted for category " + category
                                + ". Allowed types: " + ALLOWED_PHOTO_TYPES
                );
            }
            long maxPhotoBytes = (minioProperties != null) ? minioProperties.getMaxPhotoSizeBytes() : 10 * 1024 * 1024L;
            if (bytes.length > maxPhotoBytes) {
                throw new IllegalArgumentException(
                        "File size (" + bytes.length + " bytes) exceeds maximum limit of " + maxPhotoBytes
                                + " bytes for category " + category
                );
            }
        } else {
            if (!ALLOWED_DOCUMENT_TYPES.contains(normalizedType)) {
                throw new IllegalArgumentException(
                        "File content-type '" + contentType + "' is not permitted for category " + category
                                + ". Allowed types: " + ALLOWED_DOCUMENT_TYPES
                );
            }
            long maxDocBytes = (minioProperties != null) ? minioProperties.getMaxDocumentSizeBytes() : 25 * 1024 * 1024L;
            if (bytes.length > maxDocBytes) {
                throw new IllegalArgumentException(
                        "File size (" + bytes.length + " bytes) exceeds maximum limit of " + maxDocBytes
                                + " bytes for category " + category
                );
            }
        }
    }

    private void uploadToMinio(String bucket, String key, String contentType, byte[] bytes) {
        if (s3Client == null) {
            return;
        }
        try {
            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength((long) bytes.length)
                    .build();
            s3Client.putObject(putReq, RequestBody.fromBytes(bytes));
            log.debug("Successfully stored object in MinIO: s3://{}/{}", bucket, key);
        } catch (Exception ex) {
            log.warn("MinIO upload deferred or offline for key {}: {}", key, ex.getMessage());
        }
    }

    private byte[] fetchFromMinio(String bucket, String key) {
        if (s3Client == null) {
            return new byte[0];
        }
        try {
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            return s3Client.getObjectAsBytes(getReq).asByteArray();
        } catch (NoSuchKeyException ex) {
            log.warn("Object not found in MinIO bucket {}: key {}", bucket, key);
            return new byte[0];
        } catch (Exception ex) {
            log.warn("MinIO fetch failed or offline for key {}: {}", key, ex.getMessage());
            return new byte[0];
        }
    }

    private void deleteFromMinio(String bucket, String key) {
        if (s3Client == null) {
            return;
        }
        try {
            DeleteObjectRequest delReq = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            s3Client.deleteObject(delReq);
        } catch (Exception ex) {
            log.warn("MinIO delete failed or offline for key {}: {}", key, ex.getMessage());
        }
    }

    private String sanitizeFilename(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String computeSha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
