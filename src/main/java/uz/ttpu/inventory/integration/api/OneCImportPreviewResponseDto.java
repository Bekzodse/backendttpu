package uz.ttpu.inventory.integration.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OneCImportPreviewResponseDto(
        UUID stagingId,
        String fileName,
        String fileType,
        int totalRows,
        int validRowsCount,
        int errorRowsCount,
        int warningRowsCount,
        boolean canCommit,
        List<String> duplicateInventoryNumbers,
        List<String> duplicateSerialNumbers,
        List<String> missingRooms,
        List<String> missingEmployees,
        List<String> missingCategories,
        List<String> missingBuildings,
        List<OneCImportRowPreviewDto> rows,
        Instant stagedAt,
        Instant expiresAt
) {
}
