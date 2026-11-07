package uz.ttpu.inventory.inventory.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.inventory.service.InventoryService;

@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
@Tag(
    name = "14. Mobile Offline Sync & Reconciliation",
    description =
        "Versioned offline session packages for Android/iOS commission apps, idempotent batch scan"
            + " synchronization (clientActionId UUID deduplication), and 4-category session"
            + " reconciliation.")
public class OfflineSyncController {

  private final InventoryService inventoryService;

  @GetMapping("/sessions/{id}/package")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Download offline session package for mobile use",
      description =
          "Returns an inventory session's rooms, expected items, and package version number so"
              + " mobile devices can conduct room scans without network connectivity.")
  public OfflineSessionPackageDto getSessionOfflinePackage(@PathVariable("id") UUID sessionId) {
    return inventoryService.getOfflineSessionPackage(sessionId);
  }

  @GetMapping("/sessions/{id}/items/cursor")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Cursor-based pagination for mobile app syncs",
      description =
          "Returns a window of expected items using an opaque base64 cursor token to avoid offsets"
              + " on mobile network reconnections.")
  public CursorPageResponse<InventoryExpectedItemDto> getExpectedItemsCursor(
      @PathVariable("id") UUID sessionId,
      @org.springframework.web.bind.annotation.RequestParam(value = "cursor", required = false)
          String cursor,
      @org.springframework.web.bind.annotation.RequestParam(value = "limit", defaultValue = "20")
          int limit) {
    return inventoryService.getExpectedItemsCursor(sessionId, cursor, limit);
  }

  @PostMapping("/batch")
  @PreAuthorize(
      "hasAnyRole('ADMIN', 'COMMISSION_MEMBER', 'INVENTORY_COMMISSION', 'WAREHOUSE_MANAGER',"
          + " 'DEPARTMENT_HEAD')")
  @Operation(
      summary = "Submit a batch of offline scan actions idempotently",
      description =
          "Processes a list of mobile scan actions. Each action carries a clientActionId UUID to"
              + " guarantee idempotency and prevent duplicate processing on mobile network"
              + " retries.")
  public OfflineSyncBatchResponseDto syncBatch(
      @Valid @RequestBody OfflineSyncBatchRequest request) {
    return inventoryService.processOfflineBatch(request);
  }

  @PostMapping("/sessions/{id}/reconcile")
  @PreAuthorize(
      "hasAnyRole('ADMIN', 'COMMISSION_MEMBER', 'INVENTORY_COMMISSION', 'WAREHOUSE_MANAGER')")
  @Operation(
      summary = "Reconcile offline-synced inventory session",
      description =
          "Compares count results against expected lists, categorizing items into Joyida (JOYIDA),"
              + " Boshqa xonada topildi (BOSHQA_XONADA_TOPILDI), Topilmadi (TOPILMADI -> moves item"
              + " to QIDIRUVDA), and Ortiqcha (ORTIQCHA).")
  public InventoryReconciliationReportDto reconcileSession(@PathVariable("id") UUID sessionId) {
    return inventoryService.reconcileSession(sessionId);
  }
}
