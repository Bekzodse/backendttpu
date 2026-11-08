package uz.ttpu.inventory.report.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    description =
        "High-throughput Apache POI SXSSF streaming XLSX report generation (10,000+ rows in under"
            + " 30 seconds) and Thymeleaf + OpenHTMLtoPDF official PDF print forms (FT-12.9"
            + " Handover Act, FT-9.13 Opis, Comparison Statement, and Final Act) with embedded"
            + " E-IMZO PKCS#7 signature metadata.")
public class ReportController {

  private static final MediaType XLSX_MEDIA_TYPE =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final SxssfInventoryReportService reportService;
  private final PrintFormApi printFormApi;

  @GetMapping({"/inventory.xlsx", "/inventory/xlsx"})
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Stream full database inventory report as XLSX",
      description =
          "Streams all inventory items from PostgreSQL into an XLSX workbook using Apache POI"
              + " SXSSFWorkbook (window size 200), records generation metrics in"
              + " report_export_logs, and logs an EXPORT entry in audit_record.")
  public ResponseEntity<byte[]> exportDatabaseInventoryXlsx(Principal principal) {
    String username = principal != null ? principal.getName() : "ANONYMOUS";
    GeneratedXlsxReport report = reportService.generateInventoryXlsxFromDatabase(username);
    return ResponseEntity.ok()
        .contentType(XLSX_MEDIA_TYPE)
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ttpu-inventory-report.xlsx\"")
        .header("X-Report-Row-Count", String.valueOf(report.log().rowCount()))
        .header("X-Report-Generation-Ms", String.valueOf(report.log().generationTimeMs()))
        .body(report.xlsxBytes());
  }

  @PostMapping({"/inventory.xlsx", "/inventory/xlsx"})
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Generate streaming XLSX report from custom inventory rows",
      description =
          "Generates an XLSX workbook from a supplied list of InventoryReportRowDto records"
              + " (supporting 10,000+ rows in under 30 seconds via SXSSFWorkbook) and logs export"
              + " telemetry.")
  public ResponseEntity<byte[]> exportCustomInventoryXlsx(
      @RequestBody List<InventoryReportRowDto> rows, Principal principal) {
    String username = principal != null ? principal.getName() : "ANONYMOUS";
    GeneratedXlsxReport report = reportService.generateInventoryXlsxReport(rows, username);
    return ResponseEntity.ok()
        .contentType(XLSX_MEDIA_TYPE)
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ttpu-inventory-report.xlsx\"")
        .header("X-Report-Row-Count", String.valueOf(report.log().rowCount()))
        .header("X-Report-Generation-Ms", String.valueOf(report.log().generationTimeMs()))
        .body(report.xlsxBytes());
  }

  @GetMapping("/logs")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "List XLSX report export execution logs",
      description =
          "Returns historical report generation logs including row count, file size in bytes, and"
              + " generation time in milliseconds.")
  public List<ReportExportLogDto> listExportLogs() {
    return reportService.listExportLogs();
  }

  @GetMapping("/pdf/templates")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "List editable XHTML print form templates (FT-12.9 & FT-9.13)",
      description =
          "Returns all official PDF print form templates (ft-12-9-handover-act,"
              + " ft-9-13-inventory-opis, ft-9-13-comparison-statement, ft-9-13-final-act) and"
              + " their current editable XHTML source.")
  public List<PrintTemplateDto> listPrintTemplates() {
    return printFormApi.listTemplates();
  }

  @PutMapping("/pdf/templates/{templateKey}")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Update an editable XHTML print form template (FT-12.9 or FT-9.13)",
      description =
          "Updates the XHTML template used by Thymeleaf + OpenHTMLtoPDF for generating FT-12.9 or"
              + " FT-9.13 official PDF documents.")
  public PrintTemplateDto updatePrintTemplate(
      @PathVariable String templateKey, @Valid @RequestBody UpdatePrintTemplateRequest request) {
    return printFormApi.updateTemplate(templateKey, request.xhtmlContent());
  }

  @PostMapping(value = "/pdf/handover-act", produces = MediaType.APPLICATION_PDF_VALUE)
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Generate FT-12.9 Handover Act PDF (Topshirish-Qabul Qilish Dalolatnomasi)",
      description =
          "Renders the official FT-12.9 Handover Act PDF using Thymeleaf + OpenHTMLtoPDF, embeds"
              + " PDFBox metadata (including E-IMZO signatures if present), and returns the PDF"
              + " bytes.")
  public ResponseEntity<byte[]> generateHandoverActPdf(
      @RequestBody HandoverActPrintRequest request) {
    GeneratedPdfDocumentDto doc = printFormApi.generateHandoverActPdf(request);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.fileName() + "\"")
        .header("X-Document-Type", doc.documentType())
        .header("X-Form-Code", doc.formCode())
        .header("X-Document-Checksum", doc.sha256Checksum())
        .body(doc.pdfBytes());
  }

  @PostMapping(value = "/pdf/inventory-opis", produces = MediaType.APPLICATION_PDF_VALUE)
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Generate FT-9.13 Inventory List / Opis PDF (Inventarizatsiya Ro'yxati / Opis)",
      description =
          "Renders the official FT-9.13 Inventory Opis PDF containing expected and scanned room"
              + " assets.")
  public ResponseEntity<byte[]> generateInventoryOpisPdf(
      @RequestBody InventorySessionPrintRequest request) {
    GeneratedPdfDocumentDto doc = printFormApi.generateInventoryOpisPdf(request);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.fileName() + "\"")
        .header("X-Document-Type", doc.documentType())
        .header("X-Form-Code", doc.formCode())
        .header("X-Document-Checksum", doc.sha256Checksum())
        .body(doc.pdfBytes());
  }

  @PostMapping(value = "/pdf/inventory-comparison", produces = MediaType.APPLICATION_PDF_VALUE)
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Generate FT-9.13 Comparison Statement PDF (Solishtirma Vedomost)",
      description =
          "Renders the official FT-9.13 Comparison Statement PDF highlighting discrepancies,"
              + " shortages, and surplus items.")
  public ResponseEntity<byte[]> generateInventoryComparisonStatementPdf(
      @RequestBody InventorySessionPrintRequest request) {
    GeneratedPdfDocumentDto doc = printFormApi.generateInventoryComparisonStatementPdf(request);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.fileName() + "\"")
        .header("X-Document-Type", doc.documentType())
        .header("X-Form-Code", doc.formCode())
        .header("X-Document-Checksum", doc.sha256Checksum())
        .body(doc.pdfBytes());
  }

  @PostMapping(value = "/pdf/inventory-final-act", produces = MediaType.APPLICATION_PDF_VALUE)
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Generate FT-9.13 Final Inventory Act PDF (Yakuniy Inventarizatsiya Dalolatnomasi)",
      description =
          "Renders the official FT-9.13 Final Inventory Act PDF with commission approvals and"
              + " summary reconciliation stats.")
  public ResponseEntity<byte[]> generateInventoryFinalActPdf(
      @RequestBody InventorySessionPrintRequest request) {
    GeneratedPdfDocumentDto doc = printFormApi.generateInventoryFinalActPdf(request);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.fileName() + "\"")
        .header("X-Document-Type", doc.documentType())
        .header("X-Form-Code", doc.formCode())
        .header("X-Document-Checksum", doc.sha256Checksum())
        .body(doc.pdfBytes());
  }

  @PostMapping(value = "/pdf/attach-signature", produces = MediaType.APPLICATION_PDF_VALUE)
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Attach E-IMZO signature metadata to an official PDF document",
      description =
          "Validates the signature record via E-IMZO external verification stub, embeds PKCS#7"
              + " signature details into PDFBox document metadata, and returns the signed PDF.")
  public ResponseEntity<byte[]> attachSignatureToPdf(
      @Valid @RequestBody AttachPdfSignatureRequest request) {
    byte[] rawPdfBytes = Base64.getDecoder().decode(request.pdfBase64());
    String checksum = computeSha256Hex(rawPdfBytes);

    GeneratedPdfDocumentDto inputDoc =
        new GeneratedPdfDocumentDto(
            rawPdfBytes,
            request.fileName(),
            request.formCode(),
            request.documentType(),
            request.documentId(),
            request.documentNumber(),
            checksum,
            List.of(),
            java.time.Instant.now());

    GeneratedPdfDocumentDto signedDoc =
        printFormApi.attachEImzoSignatureToPdf(
            inputDoc,
            request.signerPinfl(),
            request.signerTin(),
            request.signerFullName(),
            request.signerOrganization(),
            request.certificateSerialNumber());

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + signedDoc.fileName() + "\"")
        .header("X-Document-Type", signedDoc.documentType())
        .header("X-Form-Code", signedDoc.formCode())
        .header("X-Document-Checksum", signedDoc.sha256Checksum())
        .header(
            "X-EImzo-Signature-Count",
            String.valueOf(
                signedDoc.eimzoSignatures() != null ? signedDoc.eimzoSignatures().size() : 0))
        .body(signedDoc.pdfBytes());
  }

  private String computeSha256Hex(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(bytes);
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      return "";
    }
  }
}
