package uz.ttpu.inventory.report.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.report.service.SxssfInventoryReportService;
import uz.ttpu.inventory.report.service.SxssfInventoryReportService.GeneratedXlsxReport;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(
        name = "17. Streaming XLSX Inventory Reports (Apache POI SXSSF)",
        description = "High-throughput Apache POI SXSSF streaming XLSX report generation (10,000+ rows in under 30 seconds) with export performance metrics and audit logging."
)
public class ReportController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final SxssfInventoryReportService reportService;

    @GetMapping({"/inventory.xlsx", "/inventory/xlsx"})
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Stream full database inventory report as XLSX",
            description = "Streams all inventory items from PostgreSQL into an XLSX workbook using Apache POI SXSSFWorkbook (window size 200), records generation metrics in report_export_logs, and logs an EXPORT entry in audit_record."
    )
    public ResponseEntity<byte[]> exportDatabaseInventoryXlsx(Principal principal) {
        String username = principal != null ? principal.getName() : "ANONYMOUS";
        GeneratedXlsxReport report = reportService.generateInventoryXlsxFromDatabase(username);
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ttpu-inventory-report.xlsx\"")
                .header("X-Report-Row-Count", String.valueOf(report.log().rowCount()))
                .header("X-Report-Generation-Ms", String.valueOf(report.log().generationTimeMs()))
                .body(report.xlsxBytes());
    }

    @PostMapping({"/inventory.xlsx", "/inventory/xlsx"})
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Generate streaming XLSX report from custom inventory rows",
            description = "Generates an XLSX workbook from a supplied list of InventoryReportRowDto records (supporting 10,000+ rows in under 30 seconds via SXSSFWorkbook) and logs export telemetry."
    )
    public ResponseEntity<byte[]> exportCustomInventoryXlsx(
            @RequestBody List<InventoryReportRowDto> rows,
            Principal principal
    ) {
        String username = principal != null ? principal.getName() : "ANONYMOUS";
        GeneratedXlsxReport report = reportService.generateInventoryXlsxReport(rows, username);
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ttpu-inventory-report.xlsx\"")
                .header("X-Report-Row-Count", String.valueOf(report.log().rowCount()))
                .header("X-Report-Generation-Ms", String.valueOf(report.log().generationTimeMs()))
                .body(report.xlsxBytes());
    }

    @GetMapping("/logs")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "List XLSX report export execution logs",
            description = "Returns historical report generation logs including row count, file size in bytes, and generation time in milliseconds."
    )
    public List<ReportExportLogDto> listExportLogs() {
        return reportService.listExportLogs();
    }
}
