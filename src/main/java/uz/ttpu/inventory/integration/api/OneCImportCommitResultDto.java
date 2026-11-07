package uz.ttpu.inventory.integration.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OneCImportCommitResultDto(
        UUID stagingId,
        String status,
        int totalProcessed,
        int importedCount,
        int skippedCount,
        Instant committedAt,
        List<String> importedInventoryNumbers,
        List<String> errorMessages
) {
}
