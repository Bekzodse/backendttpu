package uz.ttpu.inventory.repair.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.repair.service.RepairService;

@RestController
@RequestMapping("/api/v1/repairs")
@RequiredArgsConstructor
@Tag(
    name = "10. Repair & Vendor Maintenance Tickets",
    description =
        "Repair ticket lifecycle (NEW -> IN_PROGRESS -> IN_SERVICE -> DONE -> CLOSED) with"
            + " event-driven item state transitions to/from TAMIRDA and multi-channel"
            + " notifications.")
public class RepairController {

  private final RepairService repairService;

  @GetMapping
  @Operation(
      summary = "List all repair tickets",
      description = "Returns all repair and maintenance tickets ordered by creation timestamp.")
  public ResponseEntity<List<RepairTicketDto>> listTickets() {
    return ResponseEntity.ok(repairService.listTickets());
  }

  @GetMapping("/replacement-recommended")
  @Operation(
      summary = "List repair tickets flagged for replacement recommendation (B3.5 50% Rule)",
      description =
          "Returns all repair tickets where the estimated or actual repair cost reaches or exceeds"
              + " 50% of the asset's residual value.")
  public ResponseEntity<List<RepairTicketDto>> listReplacementRecommended() {
    return ResponseEntity.ok(repairService.listReplacementRecommended());
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get repair ticket by ID",
      description = "Retrieves a single repair ticket by its UUID.")
  public ResponseEntity<RepairTicketDto> getTicket(@PathVariable UUID id) {
    return ResponseEntity.ok(repairService.getTicket(id));
  }

  @PostMapping
  @Operation(
      summary = "Create a new repair ticket (NEW)",
      description =
          "Opens a repair ticket in NEW state for an inventory item and reports the defect"
              + " description.")
  public ResponseEntity<RepairTicketDto> createTicket(
      @Valid @RequestBody CreateRepairTicketRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(repairService.createTicket(request));
  }

  @PostMapping("/{id}/in-progress")
  @Operation(
      summary = "Start internal repair diagnosis (IN_PROGRESS)",
      description =
          "Assigns a technician and expected completion timestamp, transitioning the ticket from"
              + " NEW to IN_PROGRESS.")
  public ResponseEntity<RepairTicketDto> startInProgress(
      @PathVariable UUID id, @Valid @RequestBody StartInProgressRepairRequest request) {
    return ResponseEntity.ok(repairService.startInProgress(id, request));
  }

  @PostMapping("/{id}/vendor-repair")
  @Operation(
      summary = "Send item to external vendor repair (IN_SERVICE -> Item state TAMIRDA)",
      description =
          "Transitions the ticket to IN_SERVICE and publishes ItemMovedToVendorRepairEvent, which"
              + " transitions the Item state to TAMIRDA and dispatches Email, Telegram, and Push"
              + " notifications.")
  public ResponseEntity<RepairTicketDto> sendToVendorRepair(
      @PathVariable UUID id, @Valid @RequestBody VendorRepairRequest request) {
    return ResponseEntity.ok(repairService.sendToVendorRepair(id, request));
  }

  @PostMapping("/{id}/done")
  @Operation(
      summary = "Complete repair (DONE -> Item state FOYDALANISHDA)",
      description =
          "Records repair resolution and cost, transitions the ticket to DONE, and (if returning"
              + " from vendor repair) publishes ItemRepairCompletedEvent to restore the Item state"
              + " to FOYDALANISHDA.")
  public ResponseEntity<RepairTicketDto> completeRepair(
      @PathVariable UUID id, @Valid @RequestBody CompleteRepairRequest request) {
    return ResponseEntity.ok(repairService.completeRepair(id, request));
  }

  @PostMapping("/{id}/close")
  @Operation(
      summary = "Close a completed repair ticket (CLOSED)",
      description = "Finalizes and closes a DONE repair ticket.")
  public ResponseEntity<RepairTicketDto> closeTicket(@PathVariable UUID id) {
    return ResponseEntity.ok(repairService.closeTicket(id));
  }
}
