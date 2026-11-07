package uz.ttpu.inventory.inventory.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import uz.ttpu.inventory.inventory.service.InventoryService;

@RestController
@RequestMapping("/api/v1/inventory/sessions")
@RequiredArgsConstructor
@Tag(
        name = "13. Inventory Sessions & Live STOMP Dashboard",
        description = "University inventory audit sessions (rector's order, commission members, room snapshots), QR scanning, reconciliation, and real-time STOMP WebSocket progress broadcasts (/ws/inventory)."
)
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List all inventory sessions", description = "Returns all inventory sessions (DRAFT, IN_PROGRESS, COMPLETED, CANCELLED).")
    public List<InventorySessionDto> listSessions() {
        return inventoryService.listSessions();
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get inventory session by ID", description = "Retrieves session details including rector's order number, chairman, commission members, and assigned rooms.")
    public InventorySessionDto getSession(@PathVariable UUID sessionId) {
        return inventoryService.getSession(sessionId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'COMMISSION_MEMBER', 'INVENTORY_COMMISSION', 'WAREHOUSE_MANAGER', 'RECTOR', 'VICE_RECTOR', 'RECTORATE')")
    @Operation(
            summary = "Create a new inventory session",
            description = "Creates an inventory session with rector's order details, commission members, and rooms. If generateExpectedListImmediately=true, snapshots the expected items per room and broadcasts the initial STOMP progress dashboard."
    )
    public InventorySessionDto createSession(@Valid @RequestBody CreateInventorySessionRequest request) {
        return inventoryService.createSession(request);
    }

    @PostMapping("/{sessionId}/expected-list")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMMISSION_MEMBER', 'INVENTORY_COMMISSION', 'WAREHOUSE_MANAGER')")
    @Operation(
            summary = "Generate or refresh expected item lists per room",
            description = "Snapshots all active non-written-off items currently assigned to the session's rooms, increments packageVersion, transitions session to IN_PROGRESS, and pushes a live STOMP dashboard update."
    )
    public List<RoomExpectedItemsDto> generateExpectedList(@PathVariable UUID sessionId) {
        return inventoryService.generateExpectedListForSession(sessionId);
    }

    @GetMapping("/{sessionId}/expected-list")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Get expected item lists grouped by room",
            description = "Returns the expected item list, found/not-found counts, and room completion status for every room in the session."
    )
    public List<RoomExpectedItemsDto> getExpectedListsByRoom(@PathVariable UUID sessionId) {
        return inventoryService.getExpectedListsByRoom(sessionId);
    }

    @GetMapping("/{sessionId}/rooms/{roomId}/expected-list")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Get expected item list for a specific room",
            description = "Returns expected items and scan progress for a single room within the inventory session."
    )
    public RoomExpectedItemsDto getRoomExpectedList(
            @PathVariable UUID sessionId,
            @PathVariable UUID roomId
    ) {
        return inventoryService.getRoomExpectedList(sessionId, roomId);
    }

    @PostMapping("/{sessionId}/scan")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMMISSION_MEMBER', 'INVENTORY_COMMISSION', 'WAREHOUSE_MANAGER', 'DEPARTMENT_HEAD')")
    @Operation(
            summary = "Record a live item scan in an inventory session",
            description = "Marks an expected item as FOUND (by itemId or 10-character QR code) in the scanned room, auto-completes rooms when notFoundCount reaches 0, and broadcasts the updated InventoryProgressDashboardDto over STOMP WebSocket."
    )
    public InventoryExpectedItemDto scanItem(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ScanInventoryItemRequest request
    ) {
        return inventoryService.scanItem(sessionId, request);
    }

    @PostMapping("/{sessionId}/rooms/{roomId}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMMISSION_MEMBER', 'INVENTORY_COMMISSION', 'WAREHOUSE_MANAGER')")
    @Operation(
            summary = "Manually mark a room count as COMPLETED",
            description = "Marks a room's inventory count as COMPLETED (even if some items remain NOT_FOUND) and broadcasts the updated progress dashboard via STOMP WebSocket."
    )
    public RoomProgressDto completeRoom(
            @PathVariable UUID sessionId,
            @PathVariable UUID roomId
    ) {
        return inventoryService.completeRoom(sessionId, roomId);
    }

    @PostMapping("/{sessionId}/reconcile")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMMISSION_MEMBER', 'INVENTORY_COMMISSION', 'WAREHOUSE_MANAGER')")
    @Operation(
            summary = "Reconcile inventory session and transition missing items to QIDIRUVDA",
            description = "Compares scanned results against expected lists, categorizing items into JOYIDA (In place), BOSHQA_XONADA_TOPILDI (Found in another room), TOPILMADI (Not found -> automatically transitions item state to QIDIRUVDA), and ORTIQCHA (Surplus)."
    )
    public InventoryReconciliationReportDto reconcileSession(@PathVariable UUID sessionId) {
        return inventoryService.reconcileSession(sessionId);
    }

    @GetMapping("/{sessionId}/dashboard")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Get live inventory session progress dashboard",
            description = "Returns overall and per-room completion status, expected/found/not-found counts, and completion percentages."
    )
    public InventoryProgressDashboardDto getProgressDashboard(@PathVariable UUID sessionId) {
        return inventoryService.getProgressDashboard(sessionId);
    }

    @PostMapping("/{sessionId}/dashboard/push")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Broadcast live progress dashboard to STOMP WebSocket subscribers",
            description = "Computes the current InventoryProgressDashboardDto and pushes it to /topic/inventory/sessions/{sessionId}/progress and /topic/inventory/dashboard."
    )
    public InventoryProgressDashboardDto pushProgressDashboard(@PathVariable UUID sessionId) {
        return inventoryService.pushProgressDashboard(sessionId);
    }

    @MessageMapping("/inventory/sessions/{sessionId}/dashboard")
    public void requestDashboardPushOverStomp(@DestinationVariable UUID sessionId) {
        inventoryService.pushProgressDashboard(sessionId);
    }
}
