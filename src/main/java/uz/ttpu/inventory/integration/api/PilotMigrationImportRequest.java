package uz.ttpu.inventory.integration.api;

import jakarta.validation.constraints.NotBlank;

public record PilotMigrationImportRequest(
    @NotBlank(message = "xlsxBase64 must not be blank") String xlsxBase64,
    String defaultBuildingCode,
    boolean dryRun,
    boolean strictMode,
    boolean autoCreateMissingCategories) {}
