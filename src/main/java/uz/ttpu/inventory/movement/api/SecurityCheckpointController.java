package uz.ttpu.inventory.movement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.movement.service.OffCampusPermitService;

@RestController
@RequiredArgsConstructor
@Tag(
    name = "09. Security Checkpoint & Off-Campus Permits",
    description =
        "B3.4 Temporary off-campus permits and campus gate verification endpoint returning 'may"
            + " leave' or 'may not' on scanned QR IDs.")
public class SecurityCheckpointController {

  private final OffCampusPermitService permitService;

  @GetMapping({
    "/api/v1/security/checkpoint/{qrId}",
    "/checkpoint/{qrId}",
    "/checkpoint/permit/{qrId}",
    "/api/v1/security/checkpoint/permit/{qrId}"
  })
  @SecurityRequirements
  @Operation(
      summary = "Verify if item may leave campus (Gate scan verification)",
      description =
          "Publicly accessible gate checkpoint scan endpoint. Returns mayLeave: true ('MAY_LEAVE')"
              + " or mayLeave: false ('MAY_NOT_LEAVE') based on active permit validity.")
  public ResponseEntity<PermitCheckpointResultDto> verifyCheckpoint(@PathVariable String qrId) {
    return ResponseEntity.ok(permitService.verifySecurityCheckpoint(qrId));
  }

  @PostMapping("/api/v1/movement/permits")
  @PreAuthorize(
      "hasAnyRole('ADMIN', 'SECURITY_GUARD', 'DEPARTMENT_HEAD', 'WAREHOUSE_MANAGER', 'RECTOR',"
          + " 'VICE_RECTOR')")
  @Operation(
      summary = "Issue a temporary off-campus permit",
      description =
          "Creates a new off-campus permit for an inventory item with a validity date range.")
  public ResponseEntity<OffCampusPermitDto> createPermit(
      @Valid @RequestBody CreateOffCampusPermitRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(permitService.createPermit(request));
  }

  @GetMapping("/api/v1/movement/permits")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "List all off-campus permits",
      description = "Returns all issued off-campus permits.")
  public ResponseEntity<List<OffCampusPermitDto>> listPermits() {
    return ResponseEntity.ok(permitService.listPermits());
  }

  @PostMapping("/api/v1/movement/permits/{id}/revoke")
  @PreAuthorize("hasAnyRole('ADMIN', 'SECURITY_GUARD', 'DEPARTMENT_HEAD')")
  @Operation(
      summary = "Revoke an off-campus permit",
      description = "Immediately revokes a permit, preventing the item from leaving campus.")
  public ResponseEntity<OffCampusPermitDto> revokePermit(
      @PathVariable UUID id,
      @RequestParam(required = false, defaultValue = "Revoked by security administration")
          String reason) {
    return ResponseEntity.ok(permitService.revokePermit(id, reason));
  }
}
