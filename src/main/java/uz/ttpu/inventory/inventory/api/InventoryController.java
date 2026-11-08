package uz.ttpu.inventory.inventory.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.inventory.service.InventoryDocumentPrintService;
import uz.ttpu.inventory.inventory.service.InventoryService;

@RestController
@RequestMapping("/api/v1/inventory/sessions")
@RequiredArgsConstructor
@Tag(
    name = "13. Inventory Sessions, Live STOMP Dashboard & FT-9.13 Official PDFs",
    description =
        "University inventory audit sessions (rector's order, commission members, room snapshots),"
            + " QR scanning, reconciliation, real-time STOMP WebSocket progress broadcasts"
            + " (/ws/inventory), and official FT-9.13 PDF generation (Opis, Comparison Statement,"
            + " and Final Act) with E-IMZO digital signatures.")
public class InventoryController {

  private final InventoryService inventoryService;
  private final InventoryDocumentPrintService documentPrintService;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "List all inventory sessions",
      description = "Returns all inventory sessions (DRAFT, IN_PROGRESS, COMPLETED, CANCELLED).")
  public List<InventorySessionDto> listSessions() {
    return inventoryService.listSessions();
  }

  @GetMapping("/{sessionId}")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Get inventory session by ID",
      description =
          "Retrieves session details including rector's order number, chairman, commission members,"
              + " and assigned rooms.")
  public InventorySessionDto getSession(@PathVariable UUID sessionId) {
    return inventoryService.getSession(sessionId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(
      "hasAnyRole('ADMIN', 'COMMISSION_MEMBER', 'INVENTORY_COMMISSION', 'WAREHOUSE_MANAGER',"
          + " 'RECTOR', 'VICE_RECTOR', 'RECTORATE')")
  @Operation(
      summary = "Create a new inventory session",
      description =
          "Creates an inventory session with rector's order details, commission members, and rooms."
              + " If generateExpectedListImmediately=true, snapshots the expected items per room"
              + " and broadcasts the initial STOMP progress dashboard.")
  public InventorySessionDto createSession(
      @Valid @RequestBody CreateInventorySessionRequest request) {
    return inventoryService.createSession(request);
  }

  @PostMapping("/{sessionId}/expected-list")
  @PreAuthorize(
      "hasAnyRole('ADMIN', 'COMMISSION_MEMBER', 'INVENTORY_COMMISSION', 'WAREHOUSE_MANAGER')")
  @Operation(
      summary = "Generate or refresh expected item lists per room",
      description =
          "Snapshots all active non-written-off items currently assigned to the session's rooms,"
              + " increments packageVersion, transitions session to IN_PROGRESS, and pushes a live"
              + " STOMP dashboard update.")
  public List<RoomExpectedItemsDto> generateExpectedList(@PathVariable UUID sessionId) {
    return inventoryService.generateExpectedListForSession(sessionId);
  }

  @GetMapping("/{sessionId}/expected-list")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Get expected item lists grouped by room",
      description =
          "Returns the expected item list, found/not-found counts, and room completion status for"
              + " every room in the session.")
  public List<RoomExpectedItemsDto> getExpectedListsByRoom(@PathVariable UUID sessionId) {
    return inventoryService.getExpectedListsByRoom(sessionId);
  }

  @GetMapping("/{sessionId}/rooms/{roomId}/expected-list")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Get expected item list for a specific room",
      description =
          "Returns expected items and scan progress for a single room within the inventory"
              + " session.")
  public RoomExpectedItemsDto getRoomExpectedList(
      @PathVariable UUID sessionId, @PathVariable UUID roomId) {
    return inventoryService.getRoomExpectedList(sessionId, roomId);
  }

  @GetMapping("/rooms/{roomId}/lock")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Check whether a room is currently locked due to an active inventory session",
      description =
          "Returns whether the room is locked for transfers, along with active session code and"
              + " rector's order number.")
  public RoomLockStatusDto getRoomLockStatus(@PathVariable UUID roomId) {
    return inventoryService.getRoomLockStatus(roomId);
  }

  @PostMapping("/{sessionId}/scan")
  @PreAuthorize(
      "hasAnyRole('ADMIN', 'COMMISSION_MEMBER', 'INVENTORY_COMMISSION', 'WAREHOUSE_MANAGER',"
          + " 'DEPARTMENT_HEAD')")
  @Operation(
      summary = "Record a live item scan in an inventory session",
      description =
          "Marks an expected item as FOUND (by itemId or 10-character QR code) in the scanned room,"
              + " auto-completes rooms when notFoundCount reaches 0, and broadcasts the updated"
              + " InventoryProgressDashboardDto over STOMP WebSocket.")
  public InventoryExpectedItemDto scanItem(
      @PathVariable UUID sessionId, @Valid @RequestBody ScanInventoryItemRequest request) {
    return inventoryService.scanItem(sessionId, request);
  }

  @PostMapping("/{sessionId}/rooms/{roomId}/complete")
  @PreAuthorize(
      "hasAnyRole('ADMIN', 'COMMISSION_MEMBER', 'INVENTORY_COMMISSION', 'WAREHOUSE_MANAGER')")
  @Operation(
      summary = "Manually mark a room count as COMPLETED",
      description =
          "Marks a room's inventory count as COMPLETED (even if some items remain NOT_FOUND) and"
              + " broadcasts the updated progress dashboard via STOMP WebSocket.")
  public RoomProgressDto completeRoom(@PathVariable UUID sessionId, @PathVariable UUID roomId) {
    return inventoryService.completeRoom(sessionId, roomId);
  }

  @PostMapping("/{sessionId}/reconcile")
  @PreAuthorize(
      "hasAnyRole('ADMIN', 'COMMISSION_MEMBER', 'INVENTORY_COMMISSION', 'WAREHOUSE_MANAGER')")
  @Operation(
      summary = "Reconcile inventory session and transition missing items to QIDIRUVDA",
      description =
          "Compares scanned results against expected lists, categorizing items into JOYIDA (In"
              + " place), BOSHQA_XONADA_TOPILDI (Misplaced), TOPILMADI (Missing -> automatically"
              + " transitions item state to QIDIRUVDA), and ORTIQCHA (Surplus).")
  public InventoryReconciliationReportDto reconcileSession(@PathVariable UUID sessionId) {
    return inventoryService.reconcileSession(sessionId);
  }

  @GetMapping(value = "/{sessionId}/pdf/opis", produces = MediaType.APPLICATION_PDF_VALUE)
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Generate official FT-9.13 'Opis' (Inventory List) PDF",
      description =
          "Renders the editable FT-9.13 Inventory Opis template via Thymeleaf + OpenHTMLtoPDF for"
              + " all expected/scanned items in the session, embedding E-IMZO digital signature"
              + " metadata.")
  public ResponseEntity<byte[]> generateOpisPdf(@PathVariable UUID sessionId) {
    return toPdfResponse(documentPrintService.generateOpisPdf(sessionId));
  }

  @GetMapping(
      value = "/{sessionId}/pdf/comparison-statement",
      produces = MediaType.APPLICATION_PDF_VALUE)
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Generate official FT-9.13 'Comparison Statement' (Solishtirma Vedomost) PDF",
      description =
          "Renders the editable FT-9.13 Comparison Statement template categorizing items into"
              + " JOYIDA, BOSHQA_XONADA_TOPILDI, TOPILMADI, and ORTIQCHA, with embedded E-IMZO"
              + " signature metadata.")
  public ResponseEntity<byte[]> generateComparisonStatementPdf(@PathVariable UUID sessionId) {
    return toPdfResponse(documentPrintService.generateComparisonStatementPdf(sessionId));
  }

  @GetMapping(value = "/{sessionId}/pdf/final-act", produces = MediaType.APPLICATION_PDF_VALUE)
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Generate official FT-9.13 'Final Act' (Yakuniy Dalolatnoma) PDF",
      description =
          "Renders the editable FT-9.13 Final Inventory Act summarizing commission metadata,"
              + " rector's order, reconciliation totals, and E-IMZO PKCS#7 digital signatures.")
  public ResponseEntity<byte[]> generateFinalActPdf(@PathVariable UUID sessionId) {
    return toPdfResponse(documentPrintService.generateFinalActPdf(sessionId));
  }

  @PostMapping(
      value = "/{sessionId}/pdf/{documentType}/sign",
      produces = MediaType.APPLICATION_PDF_VALUE)
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary =
          "Attach E-IMZO PKCS#7 digital signature metadata to an inventory session PDF (opis,"
              + " comparison-statement, or final-act)",
      description =
          "Verifies the signer's E-IMZO PKCS#7 signature via EImzoService, persists an"
              + " EImzoSignatureRecord, and embeds the verified signature metadata into the"
              + " generated FT-9.13 PDF.")
  public ResponseEntity<byte[]> signSessionPdf(
      @PathVariable UUID sessionId,
      @PathVariable String documentType,
      @Valid @RequestBody InventorySignPdfRequest request) {
    return toPdfResponse(documentPrintService.signSessionPdf(sessionId, documentType, request));
  }

  @GetMapping("/{sessionId}/dashboard")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Get live inventory session progress dashboard",
      description =
          "Returns overall and per-room completion status, expected/found/not-found counts, and"
              + " completion percentages.")
  public InventoryProgressDashboardDto getProgressDashboard(@PathVariable UUID sessionId) {
    return inventoryService.getProgressDashboard(sessionId);
  }

  @PostMapping("/{sessionId}/dashboard/push")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Broadcast live progress dashboard to STOMP WebSocket subscribers",
      description =
          "Computes the current InventoryProgressDashboardDto and pushes it to"
              + " /topic/inventory/sessions/{sessionId}/progress and /topic/inventory/dashboard.")
  public InventoryProgressDashboardDto pushProgressDashboard(@PathVariable UUID sessionId) {
    return inventoryService.pushProgressDashboard(sessionId);
  }

  @MessageMapping("/inventory/sessions/{sessionId}/dashboard")
  public void requestDashboardPushOverStomp(@DestinationVariable UUID sessionId) {
    inventoryService.pushProgressDashboard(sessionId);
  }

  private ResponseEntity<byte[]> toPdfResponse(InventoryPdfDocumentDto pdf) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + pdf.fileName() + "\"")
        .header("X-Form-Code", pdf.formCode())
        .header("X-Document-Type", pdf.documentType())
        .header("X-Document-SHA256", pdf.sha256Checksum())
        .header("X-EIMZO-Signature-Count", String.valueOf(pdf.eimzoSignatureCount()))
        .header("X-EIMZO-PKCS7-Id", pdf.latestPkcs7SignatureId())
        .body(pdf.pdfBytes());
  }
}
