package uz.ttpu.inventory.integration.api;

import java.time.Instant;
import java.util.List;

public record PilotMigrationResultDto(
        int totalRowsRead,
        int validRowsCount,
        int importedItemsCount,
        int skippedRowsCount,
        int mismatchCount,
        boolean dryRun,
        boolean strictMode,
        Instant executedAt,
        List<PilotMigrationMismatchDto> mismatches,
        List<PilotMigrationRowResultDto> rowResults
) {
}
