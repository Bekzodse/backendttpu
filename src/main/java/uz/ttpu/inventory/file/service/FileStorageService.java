package uz.ttpu.inventory.file.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.file.api.StoredFileDto;
import uz.ttpu.inventory.file.api.UploadFileRequest;
import uz.ttpu.inventory.file.domain.StoredFileRecord;
import uz.ttpu.inventory.file.repository.StoredFileRecordRepository;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    public static final String DEFAULT_BUCKET_NAME = "ttpu-inventory";

    private final StoredFileRecordRepository storedFileRecordRepository;
    private final StoredFileMapper storedFileMapper;

    @Transactional
    public StoredFileDto storeFile(UploadFileRequest request, String uploadedByUsername) {
        byte[] contentBytes;
        try {
            contentBytes = Base64.getDecoder().decode(request.contentBase64().trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("contentBase64 is not valid Base64 data", ex);
        }
        if (contentBytes.length == 0) {
            throw new IllegalArgumentException("Uploaded file content must not be empty");
        }

        String sha256 = computeSha256Hex(contentBytes);
        Instant now = Instant.now();
        String sanitizedFilename = request.originalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
        String fileKey = request.category().name().toLowerCase(Locale.ROOT)
                + "/" + UUID.randomUUID()
                + "-" + sanitizedFilename;

        StoredFileRecord record = StoredFileRecord.builder()
                .fileKey(fileKey)
                .bucketName(DEFAULT_BUCKET_NAME)
                .originalFilename(request.originalFilename())
                .contentType(request.contentType())
                .fileSizeBytes(contentBytes.length)
                .sha256Checksum(sha256)
                .category(request.category())
                .referenceEntityId(request.referenceEntityId())
                .uploadedByUsername(
                        uploadedByUsername != null && !uploadedByUsername.isBlank()
                                ? uploadedByUsername
                                : "SYSTEM"
                )
                .uploadedAt(now)
                .build();

        return storedFileMapper.toDto(storedFileRecordRepository.saveAndFlush(record));
    }

    @Transactional(readOnly = true)
    public StoredFileDto getById(UUID id) {
        StoredFileRecord entity = storedFileRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StoredFileRecord", id));
        return storedFileMapper.toDto(entity);
    }

    @Transactional(readOnly = true)
    public List<StoredFileDto> listByReferenceEntity(UUID referenceEntityId) {
        return storedFileRecordRepository.findAllByReferenceEntityIdOrderByUploadedAtDesc(referenceEntityId)
                .stream()
                .map(storedFileMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StoredFileDto> listAll() {
        return storedFileRecordRepository.findAll()
                .stream()
                .map(storedFileMapper::toDto)
                .toList();
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
