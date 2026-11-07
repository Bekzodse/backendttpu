package uz.ttpu.inventory.file.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.file.service.FileStorageService;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(
        name = "11. File & Document Storage (MinIO)",
        description = "Attachment storage metadata, SHA-256 integrity checksum calculation, and MinIO object key tracking for signed PDF acts, invoices, and inspection photos."
)
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Upload and register a document or attachment",
            description = "Decodes a Base64 file payload, computes its SHA-256 checksum, assigns a MinIO bucket/object key, and persists StoredFileRecord metadata linked to an optional reference entity."
    )
    public StoredFileDto uploadFile(
            @Valid @RequestBody UploadFileRequest request,
            Principal principal
    ) {
        String actor = principal != null ? principal.getName() : "SYSTEM";
        return fileStorageService.storeFile(request, actor);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get stored file metadata by ID", description = "Retrieves stored file metadata, SHA-256 checksum, and MinIO object location by UUID.")
    public StoredFileDto getById(@PathVariable UUID id) {
        return fileStorageService.getById(id);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "List stored file records",
            description = "Returns all stored file metadata records, or filters by referenceEntityId when provided."
    )
    public List<StoredFileDto> listFiles(@RequestParam(required = false) UUID referenceEntityId) {
        if (referenceEntityId != null) {
            return fileStorageService.listByReferenceEntity(referenceEntityId);
        }
        return fileStorageService.listAll();
    }
}
