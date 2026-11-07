package uz.ttpu.inventory.report.api;

import java.time.Instant;
import java.util.UUID;
import uz.ttpu.inventory.report.domain.ReportType;

public record ReportExportLogDto(
    UUID id,
    String reportCode,
    ReportType reportType,
    int rowCount,
    long fileSizeBytes,
    long generationTimeMs,
    String generatedByUsername,
    Instant generatedAt,
    Instant createdAt) {}
