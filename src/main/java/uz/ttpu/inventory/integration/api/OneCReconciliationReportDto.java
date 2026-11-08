package uz.ttpu.inventory.integration.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OneCReconciliationReportDto(
    UUID reconciliationId,
    Instant executedAt,
    int totalLocalItems,
    int totalOneCItems,
    int matchedCount,
    int mismatchCount,
    List<OneCReconciliationMismatchDto> mismatches,
    String status) {}
