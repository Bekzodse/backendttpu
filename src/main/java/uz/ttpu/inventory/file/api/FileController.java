package uz.ttpu.inventory.file.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uz.ttpu.inventory.file.domain.FileCategory;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(
    name = "11. File & Document Storage (MinIO)",
    description =
        "Attachment storage metadata, SHA-256 integrity checksum calculation, S3 object key"
            + " tracking for photos, scanned documents, and signed PDFs with allowed type and size"
            + " validations.")
public class FileController {

  private final FileStorageApi fileStorageApi;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Upload and register an attachment (Base64 JSON)",
      description =
          "Decodes a Base64 file payload, validates MIME type and category file size limits,"
              + " computes SHA-256, assigns S3 key, and persists to MinIO.")
  public StoredFileDto uploadFile(
      @Valid @RequestBody UploadFileRequest request, Principal principal) {
    String actor = principal != null ? principal.getName() : "SYSTEM";
    return fileStorageApi.storeFile(request, actor);
  }

  @PostMapping(value = "/upload-raw", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Upload attachment via multipart/form-data",
      description =
          "Direct multipart file upload with content-type and size validation (e.g. photos up to"
              + " 10MB, documents up to 25MB).")
  public StoredFileDto uploadRawFile(
      @RequestParam("file") MultipartFile file,
      @RequestParam("category") FileCategory category,
      @RequestParam(value = "referenceEntityId", required = false) UUID referenceEntityId,
      Principal principal)
      throws IOException {
    String actor = principal != null ? principal.getName() : "SYSTEM";
    return fileStorageApi.storeRawBytes(
        file.getOriginalFilename(),
        file.getContentType(),
        category,
        file.getBytes(),
        referenceEntityId,
        actor);
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Get stored file metadata by ID",
      description =
          "Retrieves stored file metadata, SHA-256 checksum, and MinIO object location by UUID.")
  public StoredFileDto getById(@PathVariable UUID id) {
    return fileStorageApi.getMetadata(id);
  }

  @GetMapping("/{id}/download")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Download stored file",
      description = "Streams the file binary from MinIO S3 bucket with attachment header and ETag.")
  public ResponseEntity<Resource> downloadFile(@PathVariable UUID id) {
    FileDownloadDto file = fileStorageApi.downloadFile(id);
    ByteArrayResource resource = new ByteArrayResource(file.data());

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .contentLength(file.fileSizeBytes())
        .eTag(file.sha256Checksum())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + file.originalFilename() + "\"")
        .body(resource);
  }

  @GetMapping("/{id}/preview")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Preview stored file inline",
      description = "Streams file content inline for in-browser PDF or photo viewing.")
  public ResponseEntity<Resource> previewFile(@PathVariable UUID id) {
    FileDownloadDto file = fileStorageApi.downloadFile(id);
    ByteArrayResource resource = new ByteArrayResource(file.data());

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .contentLength(file.fileSizeBytes())
        .eTag(file.sha256Checksum())
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.originalFilename() + "\"")
        .body(resource);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
  @Operation(
      summary = "Delete stored file",
      description = "Deletes the object from MinIO S3 bucket and removes its database record.")
  public void deleteFile(@PathVariable UUID id, Principal principal) {
    String actor = principal != null ? principal.getName() : "SYSTEM";
    fileStorageApi.deleteFile(id, actor);
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "List stored file records",
      description =
          "Returns all stored file metadata records, or filters by referenceEntityId when"
              + " provided.")
  public List<StoredFileDto> listFiles(@RequestParam(required = false) UUID referenceEntityId) {
    if (referenceEntityId != null) {
      return fileStorageApi.listByReferenceEntity(referenceEntityId);
    }
    return fileStorageApi.listAll();
  }
}
