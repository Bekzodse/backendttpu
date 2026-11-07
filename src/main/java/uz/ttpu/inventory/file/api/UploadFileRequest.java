package uz.ttpu.inventory.file.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import uz.ttpu.inventory.file.domain.FileCategory;

public record UploadFileRequest(
    @NotBlank String originalFilename,
    @NotBlank String contentType,
    @NotBlank String contentBase64,
    @NotNull FileCategory category,
    UUID referenceEntityId) {}
