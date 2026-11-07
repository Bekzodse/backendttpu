package uz.ttpu.inventory.report.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.common.domain.AuditActionType;
import uz.ttpu.inventory.common.domain.AuditableItemAction;
import uz.ttpu.inventory.report.api.InventoryReportRowDto;
import uz.ttpu.inventory.report.api.ReportExportLogDto;
import uz.ttpu.inventory.report.domain.ReportExportLog;
import uz.ttpu.inventory.report.domain.ReportType;
import uz.ttpu.inventory.report.repository.ReportExportLogRepository;

@Service
@RequiredArgsConstructor
public class SxssfInventoryReportService {

  public static final int SXSSF_ROW_ACCESS_WINDOW_SIZE = 200;

  private static final String[] HEADERS = {
    "#",
    "Inventory Number",
    "QR Code",
    "Item Name",
    "Serial Number",
    "State",
    "Purchase Price (UZS)",
    "Residual Value (UZS)",
    "Building ID",
    "Room ID",
    "Department ID",
    "Custodian Employee ID",
    "Commissioned At (UTC)"
  };

  private final ReportExportLogRepository reportExportLogRepository;
  private final AssetLogisticsApi assetLogisticsApi;
  private final ReportMapper reportMapper;

  public record GeneratedXlsxReport(byte[] xlsxBytes, ReportExportLogDto log) {}

  @Transactional
  @AuditableItemAction(AuditActionType.EXPORT)
  public GeneratedXlsxReport generateInventoryXlsxFromDatabase(String generatedBy) {
    List<ItemDto> items = assetLogisticsApi.listAllItemsForReporting();
    List<InventoryReportRowDto> rows =
        items.stream()
            .map(
                i ->
                    new InventoryReportRowDto(
                        i.inventoryNumber(),
                        i.qrCode(),
                        i.name(),
                        i.serialNumber(),
                        i.stateName(),
                        i.purchasePrice(),
                        i.residualValue(),
                        i.buildingId(),
                        i.roomId(),
                        i.departmentId(),
                        i.custodianEmployeeId(),
                        i.commissionedAt()))
            .toList();
    return generateInventoryXlsxReport(rows, generatedBy);
  }

  @Transactional
  @AuditableItemAction(AuditActionType.EXPORT)
  public GeneratedXlsxReport generateInventoryXlsxReport(
      List<InventoryReportRowDto> rows, String generatedBy) {
    long startNanos = System.nanoTime();
    byte[] xlsxBytes;

    try (SXSSFWorkbook workbook = new SXSSFWorkbook(SXSSF_ROW_ACCESS_WINDOW_SIZE);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(16384, rows.size() * 96))) {
      workbook.setCompressTempFiles(false);

      Sheet sheet = workbook.createSheet("TTPU Inventory Report");
      CellStyle headerStyle = workbook.createCellStyle();
      Font headerFont = workbook.createFont();
      headerFont.setBold(true);
      headerStyle.setFont(headerFont);

      Row headerRow = sheet.createRow(0);
      for (int col = 0; col < HEADERS.length; col++) {
        Cell cell = headerRow.createCell(col);
        cell.setCellValue(HEADERS[col]);
        cell.setCellStyle(headerStyle);
      }

      int rowIndex = 1;
      for (InventoryReportRowDto item : rows) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(rowIndex);
        row.createCell(1).setCellValue(nullSafe(item.inventoryNumber()));
        row.createCell(2).setCellValue(nullSafe(item.qrCode()));
        row.createCell(3).setCellValue(nullSafe(item.name()));
        row.createCell(4).setCellValue(nullSafe(item.serialNumber()));
        row.createCell(5).setCellValue(nullSafe(item.state()));
        row.createCell(6).setCellValue(toDouble(item.purchasePrice()));
        row.createCell(7).setCellValue(toDouble(item.residualValue()));
        row.createCell(8)
            .setCellValue(item.buildingId() != null ? item.buildingId().toString() : "");
        row.createCell(9).setCellValue(item.roomId() != null ? item.roomId().toString() : "");
        row.createCell(10)
            .setCellValue(item.departmentId() != null ? item.departmentId().toString() : "");
        row.createCell(11)
            .setCellValue(
                item.custodianEmployeeId() != null ? item.custodianEmployeeId().toString() : "");
        row.createCell(12)
            .setCellValue(item.commissionedAt() != null ? item.commissionedAt().toString() : "");
        rowIndex++;
      }

      workbook.write(out);
      xlsxBytes = out.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to generate SXSSF XLSX inventory report", ex);
    }

    long elapsedMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
    Instant now = Instant.now();

    ReportExportLog logEntity =
        ReportExportLog.builder()
            .reportCode("RPT-XLSX-" + now.toEpochMilli())
            .reportType(ReportType.INVENTORY_ITEMS_XLSX)
            .rowCount(rows.size())
            .fileSizeBytes(xlsxBytes.length)
            .generationTimeMs(elapsedMs)
            .generatedByUsername(
                generatedBy != null && !generatedBy.isBlank() ? generatedBy : "SYSTEM")
            .generatedAt(now)
            .build();

    ReportExportLog savedLog = reportExportLogRepository.saveAndFlush(logEntity);
    return new GeneratedXlsxReport(xlsxBytes, reportMapper.toDto(savedLog));
  }

  @Transactional(readOnly = true)
  public List<ReportExportLogDto> listExportLogs() {
    return reportExportLogRepository.findAllByOrderByGeneratedAtDesc().stream()
        .map(reportMapper::toDto)
        .toList();
  }

  private String nullSafe(String val) {
    return val != null ? val : "";
  }

  private double toDouble(BigDecimal amount) {
    return amount != null ? amount.doubleValue() : 0.0d;
  }
}
