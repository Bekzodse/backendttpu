package uz.ttpu.inventory.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.file.api.FileDownloadDto;
import uz.ttpu.inventory.file.api.StoredFileDto;
import uz.ttpu.inventory.file.api.UploadFileRequest;
import uz.ttpu.inventory.file.domain.FileCategory;
import uz.ttpu.inventory.file.domain.StoredFileRecord;
import uz.ttpu.inventory.file.repository.StoredFileRecordRepository;
import uz.ttpu.inventory.file.service.FileStorageService;
import uz.ttpu.inventory.file.service.MinioProperties;
import uz.ttpu.inventory.file.service.StoredFileMapper;

class FileStorageServiceTest {

    private StoredFileRecordRepository repository;
    private S3Client s3Client;
    private MinioProperties minioProperties;
    private FileStorageService service;

    @BeforeEach
    void setUp() {
        repository = mock(StoredFileRecordRepository.class);
        s3Client = mock(S3Client.class);
        minioProperties = new MinioProperties();
        minioProperties.setMaxPhotoSizeBytes(1024 * 1024L); // 1MB for test
        minioProperties.setMaxDocumentSizeBytes(2 * 1024 * 1024L); // 2MB for test

        when(repository.saveAndFlush(any(StoredFileRecord.class))).thenAnswer(inv -> {
            StoredFileRecord rec = inv.getArgument(0);
            if (rec.getId() == null) {
                rec.setId(UUID.randomUUID());
            }
            return rec;
        });

        StoredFileMapper mapper = Mappers.getMapper(StoredFileMapper.class);
        service = new FileStorageService(repository, mapper, minioProperties, s3Client);
    }

    @Test
    @DisplayName("Stores file, uploads to MinIO S3, computes SHA-256, and sanitizes filename")
    void storesFileComputesSha256SanitizesFilenameAndUploadsToMinio() {
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

        assertThat(stored.bucketName()).isEqualTo(minioProperties.getBucketName());
        assertThat(stored.fileKey()).startsWith("signed_pdf_act/").endsWith("_Act__12__signed__.pdf".substring(1));
        assertThat(stored.fileSizeBytes()).isEqualTo(sampleBytes.length);
        assertThat(stored.sha256Checksum()).hasSize(64);
        assertThat(stored.uploadedByUsername()).isEqualTo("SYSTEM");

        // Verify MinIO S3 putObject was called
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("Rejects disallowed MIME types for photo and document categories")
    void rejectsDisallowedMimeTypes() {
        UUID refId = UUID.randomUUID();
        String validBase64 = Base64.getEncoder().encodeToString("data".getBytes(StandardCharsets.UTF_8));

        // PDF is not allowed for ITEM_PHOTO
        assertThatThrownBy(() -> service.storeFile(new UploadFileRequest(
                "photo.pdf", "application/pdf", validBase64, FileCategory.ITEM_PHOTO, refId
        ), "user1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not permitted for category ITEM_PHOTO");

        // Executable / video is not allowed for SIGNED_PDF_ACT
        assertThatThrownBy(() -> service.storeFile(new UploadFileRequest(
                "video.mp4", "video/mp4", validBase64, FileCategory.SIGNED_PDF_ACT, refId
        ), "user1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not permitted for category SIGNED_PDF_ACT");
    }

    @Test
    @DisplayName("Rejects files that exceed size limit")
    void rejectsFilesExceedingSizeLimit() {
        UUID refId = UUID.randomUUID();
        // 1.5 MB exceeds 1MB limit for photos
        byte[] largeBytes = new byte[1500 * 1024];
        String largeBase64 = Base64.getEncoder().encodeToString(largeBytes);

        assertThatThrownBy(() -> service.storeFile(new UploadFileRequest(
                "large.jpg", "image/jpeg", largeBase64, FileCategory.ITEM_PHOTO, refId
        ), "user1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum limit");
    }

    @Test
    @DisplayName("Rejects invalid Base64 data and empty files")
    void rejectsInvalidBase64AndEmptyFiles() {
        UUID refId = UUID.randomUUID();

        assertThatThrownBy(() -> service.storeFile(new UploadFileRequest(
                "bad.pdf", "application/pdf", "%%%NOT_BASE64%%%", FileCategory.SIGNED_PDF_ACT, refId
        ), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not valid Base64");

        assertThatThrownBy(() -> service.storeFile(new UploadFileRequest(
                "empty.pdf", "application/pdf", "", FileCategory.SIGNED_PDF_ACT, refId
        ), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content must not be empty");
    }

    @Test
    @DisplayName("Downloads file from MinIO and deletes file record")
    void downloadsFileAndDeletes() {
        UUID fileId = UUID.randomUUID();
        byte[] payload = "Downloaded PDF Content".getBytes(StandardCharsets.UTF_8);
        StoredFileRecord record = StoredFileRecord.builder()
                .fileKey("signed_pdf_act/test.pdf")
                .bucketName("ttpu-inventory")
                .originalFilename("test.pdf")
                .contentType("application/pdf")
                .fileSizeBytes(payload.length)
                .sha256Checksum("some-sha256")
                .category(FileCategory.SIGNED_PDF_ACT)
                .uploadedByUsername("admin")
                .build();
        record.setId(fileId);

        when(repository.findById(fileId)).thenReturn(Optional.of(record));
        GetObjectResponse getResp = GetObjectResponse.builder().build();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(getResp, payload));

        FileDownloadDto download = service.downloadFile(fileId);
        assertThat(download.id()).isEqualTo(fileId);
        assertThat(download.data()).isEqualTo(payload);

        // Delete file
        service.deleteFile(fileId, "admin");
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        verify(repository).delete(record);
    }
}
