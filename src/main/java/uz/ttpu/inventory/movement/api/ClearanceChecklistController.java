package uz.ttpu.inventory.movement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clearance/checklists")
@RequiredArgsConstructor
@Tag(
    name = "08. Offboarding Clearance Checklist (Aylanma varaqa)",
    description =
        "B3.3 Clearance checklist opened upon HEMIS employee dismissal. Hard-blocks closure until"
            + " 100% of assigned inventory is handed over or transferred.")
public class ClearanceChecklistController {

  private final ClearanceChecklistApi checklistApi;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "List all clearance checklists",
      description = "Returns all employee offboarding checklists.")
  public ResponseEntity<List<ClearanceChecklistDto>> listChecklists() {
    return ResponseEntity.ok(checklistApi.listChecklists());
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Get clearance checklist by ID",
      description = "Retrieves an offboarding checklist with live remaining items count.")
  public ResponseEntity<ClearanceChecklistDto> getChecklist(@PathVariable UUID id) {
    return ResponseEntity.ok(checklistApi.getChecklist(id));
  }

  @GetMapping("/employee/{employeeId}")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Get active clearance checklist for an employee",
      description = "Returns the active OPEN clearance checklist for the specified employee UUID.")
  public ResponseEntity<ClearanceChecklistDto> getActiveChecklistForEmployee(
      @PathVariable UUID employeeId) {
    return checklistApi
        .getActiveChecklistForEmployee(employeeId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping("/open")
  @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'DEPARTMENT_HEAD', 'RECTOR', 'VICE_RECTOR')")
  @Operation(
      summary = "Open clearance checklist for dismissed employee",
      description =
          "Creates an Aylanma varaqa record in OPEN state and calculates the total assigned"
              + " inventory items.")
  public ResponseEntity<ClearanceChecklistDto> openChecklist(
      @Valid @RequestBody OpenClearanceChecklistRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(checklistApi.openChecklist(request));
  }

  @PostMapping("/{id}/close")
  @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'DEPARTMENT_HEAD', 'WAREHOUSE_MANAGER')")
  @Operation(
      summary = "Close clearance checklist (hard-blocks if assigned items remain)",
      description =
          "Attempts to close the checklist. Strictly throws HTTP 422"
              + " ClearanceChecklistBlockedException if any inventory items remain assigned to the"
              + " employee.")
  public ResponseEntity<ClearanceChecklistDto> closeChecklist(
      @PathVariable UUID id,
      @RequestBody(required = false) CloseClearanceChecklistRequest request) {
    return ResponseEntity.ok(checklistApi.closeChecklist(id, request));
  }
}
