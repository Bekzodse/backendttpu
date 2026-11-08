package uz.ttpu.inventory.integration.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uz.ttpu.inventory.integration.domain.ExternalSystemType;
import uz.ttpu.inventory.integration.domain.OutboxStatus;
import uz.ttpu.inventory.integration.service.EImzoService;
import uz.ttpu.inventory.integration.service.HemisSyncService;
import uz.ttpu.inventory.integration.service.OneCImportService;
import uz.ttpu.inventory.integration.service.OutboxWorkerService;
import uz.ttpu.inventory.integration.service.PilotDataMigrationService;

@RestController
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
@Tag(
    name = "15. External Integrations: Transactional Outbox, 1C, HEMIS & E-IMZO",
    description =
        "Transactional Outbox queue with Resilience4j Retry + CircuitBreaker, 1C Accounting item"
            + " export & depreciation ingestion, HEMIS daily sync, E-IMZO signatures, and B2.2"
            + " Two-Phase 1C XLSX/CSV Import.")
public class IntegrationController {

  private final OutboxWorkerService outboxWorkerService;
  private final OneCConnector oneCConnector;
  private final HemisSyncService hemisSyncService;
  private final EImzoService eImzoService;
  private final PilotDataMigrationService pilotDataMigrationService;
  private final OneCImportService oneCImportService;

  @GetMapping("/outbox")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "List transactional outbox messages",
      description =
          "Returns outbox messages optionally filtered by status (PENDING, PROCESSING, PROCESSED,"
              + " FAILED, DEAD_LETTER) and targetSystem (ONE_C, HEMIS, E_IMZO).")
  public List<OutboxMessageDto> listOutboxMessages(
      @RequestParam(required = false) OutboxStatus status,
      @RequestParam(required = false) ExternalSystemType targetSystem) {
    return outboxWorkerService.listOutboxMessages(status, targetSystem);
  }

  @GetMapping("/outbox/{id}")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Get transactional outbox message by ID",
      description =
          "Retrieves a single outbox message including retry count, backoff schedule, and last"
              + " error message.")
  public OutboxMessageDto getOutboxMessage(@PathVariable UUID id) {
    return outboxWorkerService.getOutboxMessage(id);
  }

  @PostMapping("/outbox")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'WAREHOUSE_MANAGER')")
  @Operation(
      summary = "Enqueue a message into the transactional outbox",
      description =
          "Idempotently enqueues an outgoing integration payload using a unique idempotencyKey.")
  public OutboxMessageDto enqueueOutboxMessage(@Valid @RequestBody EnqueueOutboxRequest request) {
    return outboxWorkerService.enqueueOutboxMessage(request);
  }

  @PostMapping("/outbox/process")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
  @Operation(
      summary = "Trigger Resilience4j transactional outbox worker",
      description =
          "Polls due PENDING/FAILED outbox messages and dispatches them through"
              + " ExternalSystemGateway protected by Resilience4j Retry and CircuitBreaker.")
  public OutboxWorkerRunSummaryDto triggerOutboxWorker() {
    return outboxWorkerService.processPendingOutboxMessages();
  }

  @PostMapping("/outbox/{id}/retry")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
  @Operation(
      summary = "Reset and retry a failed or dead-letter outbox message",
      description =
          "Resets message status to PENDING, schedules immediate next attempt, and clears last"
              + " error.")
  public OutboxMessageDto retryOutboxMessage(@PathVariable UUID id) {
    return outboxWorkerService.retryOutboxMessage(id);
  }

  @GetMapping("/outbox/circuit-breakers")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
  @Operation(
      summary =
          "Get live Resilience4j CircuitBreaker metrics and states for external worker targets",
      description =
          "Returns the real-time circuit breaker states, failure rates, and call counters for"
              + " ONE_C, HEMIS, and E_IMZO.")
  public Map<String, Object> getCircuitBreakers() {
    return outboxWorkerService.getCircuitBreakerDetails();
  }

  @PostMapping("/1c/items")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'WAREHOUSE_MANAGER')")
  @Operation(
      summary = "Export a new inventory item to 1C Accounting",
      description =
          "Enqueues a new asset card export payload into the Resilience4j transactional outbox"
              + " targeting 1C Accounting.")
  public OutboxMessageDto sendNewItemToOneC(@Valid @RequestBody OneCNewItemExportRequest request) {
    return oneCConnector.sendNewItemToOneC(request);
  }

  @PostMapping("/1c/depreciation")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
  @Operation(
      summary = "Receive actual depreciation updates from 1C Accounting",
      description =
          "Applies incoming 1C depreciation records to update Item.residualValue (NUMERIC(18,2))"
              + " and enqueues a 1C acknowledgment outbox message.")
  public List<OneCDepreciationRecordDto> receiveOneCDepreciation(
      @Valid @RequestBody List<OneCDepreciationRecordDto> records) {
    return oneCConnector.receiveDepreciationData(records);
  }

  // -------------------------------------------------------------------------
  // B2.2 1C Import (XLSX & CSV) Two-Phase Commit Workflow
  // -------------------------------------------------------------------------

  @PostMapping("/1c/preview")
  @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_ADMIN', 'ACCOUNTANT', 'WAREHOUSE_MANAGER')")
  @Operation(
      summary = "1C File Import Phase 1: Upload & Error Preview (XLSX or CSV)",
      description =
          "Parses 1C export file (Base64 JSON), maps columns, validates rows, flags duplicate"
              + " inventory and serial numbers (in-file & database), checks missing"
              + " rooms/employees, and returns a staged error preview WITHOUT committing to the"
              + " database.")
  public OneCImportPreviewResponseDto previewOneCImport(
      @Valid @RequestBody OneCImportPreviewRequest request) {
    return oneCImportService.createPreview(request);
  }

  @PostMapping(value = "/1c/preview-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_ADMIN', 'ACCOUNTANT', 'WAREHOUSE_MANAGER')")
  @Operation(
      summary = "1C File Import Phase 1: Multipart file upload & preview",
      description =
          "Direct multipart file upload of 1C XLSX or CSV file for validation and preview without"
              + " database commitment.")
  public OneCImportPreviewResponseDto previewOneCImportMultipart(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "defaultBuildingCode", required = false) String defaultBuildingCode,
      @RequestParam(value = "autoCreateMissingCategories", defaultValue = "false")
          boolean autoCreateMissingCategories)
      throws IOException {
    return oneCImportService.createPreviewFromBytes(
        file.getBytes(),
        file.getOriginalFilename(),
        defaultBuildingCode,
        autoCreateMissingCategories);
  }

  @GetMapping("/1c/preview/{stagingId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_ADMIN', 'ACCOUNTANT', 'WAREHOUSE_MANAGER')")
  @Operation(
      summary = "Get staged 1C import preview details",
      description = "Returns current validation report and preview rows for the given stagingId.")
  public OneCImportPreviewResponseDto getStagedPreview(@PathVariable UUID stagingId) {
    return oneCImportService.getStagedPreview(stagingId);
  }

  @PostMapping("/1c/confirm/{stagingId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_ADMIN', 'ACCOUNTANT', 'WAREHOUSE_MANAGER')")
  @Operation(
      summary = "1C File Import Phase 2: Single-Transaction Commit",
      description =
          "Commits the validated staging session into asset and reference tables in a SINGLE atomic"
              + " database transaction. If skipInvalidRows is true, valid items are committed and"
              + " invalid rows are skipped.")
  public OneCImportCommitResultDto confirmAndCommitOneCImport(
      @PathVariable UUID stagingId,
      @RequestBody(required = false) OneCImportConfirmRequest request) {
    return oneCImportService.confirmAndCommitImport(
        stagingId, request != null ? request : new OneCImportConfirmRequest(true, false));
  }

  @DeleteMapping("/1c/preview/{stagingId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_ADMIN', 'ACCOUNTANT', 'WAREHOUSE_MANAGER')")
  @Operation(
      summary = "Cancel and discard staged 1C import preview",
      description = "Removes the staged session from memory without committing any changes.")
  public void cancelStagedPreview(@PathVariable UUID stagingId) {
    oneCImportService.cancelStagedImport(stagingId);
  }

  @GetMapping("/1c/sample-template.csv")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Download sample 1C Import CSV template",
      description = "Returns a sample CSV template for 1C asset initial data import.")
  public ResponseEntity<byte[]> downloadSampleCsv() {
    byte[] csv = oneCImportService.generateSampleCsv();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"1c-import-template.csv\"")
        .body(csv);
  }

  // -------------------------------------------------------------------------
  // HEMIS & E-IMZO
  // -------------------------------------------------------------------------

  @PostMapping("/hemis/sync")
  @PreAuthorize("hasAnyRole('ADMIN', 'RECTOR', 'VICE_RECTOR', 'HR_MANAGER', 'RECTORATE')")
  @Operation(
      summary = "Trigger HEMIS department and employee synchronization",
      description =
          "Fetches department and employee updates from HEMIS (wrapped in Resilience4j Retry +"
              + " CircuitBreaker) and upserts them via ReferenceSyncApi.")
  public HemisSyncResultDto triggerDailyHemisSync() {
    return hemisSyncService.syncDailyHemisUpdates();
  }

  @PostMapping("/hemis/employees/{employeeId}/dismiss")
  @PreAuthorize("hasAnyRole('ADMIN', 'RECTOR', 'VICE_RECTOR', 'HR_MANAGER', 'RECTORATE')")
  @Operation(
      summary = "Trigger HEMIS dismissal and open Clearance Checklist (B3.3)",
      description =
          "Simulates an incoming HEMIS dismissal webhook/event, opening an official Aylanma varaqa"
              + " checklist.")
  public HemisDismissalResultDto triggerHemisDismissal(
      @PathVariable UUID employeeId,
      @RequestParam(required = false, defaultValue = "HEMIS-DISMISS-ORDER") String orderNumber,
      @RequestParam(required = false, defaultValue = "Employment contract terminated in HEMIS")
          String reason) {
    return hemisSyncService.dismissEmployee(employeeId, orderNumber, reason);
  }

  @PostMapping("/eimzo/verify-pdf")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Verify a signed PDF blob via E-IMZO and record signer details",
      description =
          "Computes the SHA-256 hash of the Base64 PDF payload, verifies the PKCS#7 signature via"
              + " E-IMZO (protected by Resilience4j), and persists signer PINFL, TIN, full name,"
              + " organization, and certificate serial.")
  public EImzoSignatureRecordDto verifySignedPdf(
      @Valid @RequestBody VerifySignedPdfRequest request) {
    return eImzoService.verifyAndRecordSignedPdf(request);
  }

  @GetMapping("/eimzo/documents/{documentId}/signatures")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "List verified E-IMZO digital signatures for a document",
      description =
          "Returns all recorded E-IMZO PKCS#7 signature verification records for the specified"
              + " documentId.")
  public List<EImzoSignatureRecordDto> listDocumentSignatures(@PathVariable UUID documentId) {
    return eImzoService.listSignaturesForDocument(documentId);
  }

  @PostMapping("/pilot-migration/import-xlsx")
  @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_ADMIN', 'ACCOUNTANT', 'WAREHOUSE_MANAGER')")
  @Operation(
      summary = "Import or validate 1C Pilot Building XLSX export (Prompt 10 / M7)",
      description =
          "Reads pilot building asset data from a Base64-encoded 1C export XLSX file, validates"
              + " rows, flags mismatches, and maps valid rows to reference and asset tables.")
  public PilotMigrationResultDto importPilotBuildingXlsx(
      @Valid @RequestBody PilotMigrationImportRequest request) {
    return pilotDataMigrationService.importPilotBuildingXlsx(request);
  }

  @GetMapping("/pilot-migration/template.xlsx")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Download sample 1C Pilot Building XLSX migration template",
      description =
          "Generates and downloads a ready-to-fill 15-column 1C pilot asset migration .xlsx"
              + " template.")
  public ResponseEntity<byte[]> downloadPilotMigrationTemplate() {
    byte[] xlsxBytes = pilotDataMigrationService.generateSamplePilot1CXlsxTemplate();
    return ResponseEntity.ok()
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"ttpu-1c-pilot-migration-template.xlsx\"")
        .body(xlsxBytes);
  }
}
