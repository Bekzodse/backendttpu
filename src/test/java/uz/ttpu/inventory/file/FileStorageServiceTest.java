package uz.ttpu.inventory.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.file.api.StoredFileDto;
import uz.ttpu.inventory.file.api.UploadFileRequest;
import uz.ttpu.inventory.file.domain.FileCategory;
import uz.ttpu.inventory.file.domain.StoredFileRecord;
import uz.ttpu.inventory.file.repository.StoredFileRecordRepository;
import uz.ttpu.inventory.file.service.FileStorageService;
import uz.ttpu.inventory.file.service.StoredFileMapper;

class FileStorageServiceTest {

    private StoredFileRecordRepository repository;
    private FileStorageService service;

    @BeforeEach
    void setUp() {
        repository = mock(StoredFileRecordRepository.class);
        when(repository.saveAndFlush(any(StoredFileRecord.class))).thenAnswer(inv -> {
            StoredFileRecord rec = inv.getArgument(0);
            if (rec.getId() == null) {
                rec.setId(UUID.randomUUID());
            }
            return rec;
        });
        StoredFileMapper mapper = Mappers.getMapper(StoredFileMapper.class);
        service = new FileStorageService(repository, mapper);
    }

    @Test
    void storesFileComputesSha256SanitizesFilenameAndRejectsInvalidBase64() {
        byte[] sampleBytes = "%PDF-1.7 signed inspection act".getBytes(StandardCharsets.UTF_8);
        String base64 = Base64.getEncoder().encodeToString(sampleBytes);
        UUID refId = UUID.randomUUID();

        StoredFileDto stored = service.storeFile(new UploadFileRequest(
                "Act #12 (signed)!.pdf",
                "application/pdf",
                base64,
                FileCategory.SIGNED_PDF_ACT,
                refId
        ), null);

        assertThat(stored.bucketName()).isEqualTo(FileStorageService.DEFAULT_BUCKET_NAME);
        assertThat(stored.fileKey()).startsWith("signed_pdf_act/").endsWith("_Act__12__signed__.pdf".substring(1));
        assertThat(stored.fileSizeBytes()).isEqualTo(sampleBytes.length);
        assertThat(stored.sha256Checksum()).hasSize(64);
        assertThat(stored.uploadedByUsername()).isEqualTo("SYSTEM");

        // Reject invalid Base64
        assertThatThrownBy(() -> service.storeFile(new UploadFileRequest(
                "bad.pdf", "application/pdf", "%%%NOT_BASE64%%%", FileCategory.ITEM_PHOTO, refId
        ), "admin")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not valid Base64");

        // Missing file throws ResourceNotFoundException
        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(missingId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
