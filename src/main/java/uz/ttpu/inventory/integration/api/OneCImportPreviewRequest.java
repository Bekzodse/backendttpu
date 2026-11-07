package uz.ttpu.inventory.integration.api;

import jakarta.validation.constraints.NotBlank;

public record OneCImportPreviewRequest(
    @NotBlank String fileContentBase64,
    String fileName,
    String defaultBuildingCode,
    boolean autoCreateMissingCategories) {}
