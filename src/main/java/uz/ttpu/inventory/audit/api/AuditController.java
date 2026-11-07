package uz.ttpu.inventory.audit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.audit.service.AuditService;
import uz.ttpu.inventory.common.domain.AuditActionType;

@RestController
@RequestMapping("/api/v1/audit/records")
@RequiredArgsConstructor
@Tag(
    name = "06. Item Card Audit Trail",
    description =
        "Immutable AOP-captured audit logs (READ, CHANGE, EXPORT) recorded in audit_record for item"
            + " cards and inventory exports.")
public class AuditController {

  private final AuditService auditService;

  @GetMapping
  @Operation(
      summary = "Query item card audit trail records",
      description =
          "Returns AOP-recorded audit logs optionally filtered by targetId (item UUID or"
              + " 10-character QR code) and actionType (READ, CHANGE, EXPORT).")
  public ResponseEntity<List<AuditRecordDto>> listAuditRecords(
      @RequestParam(required = false) String targetId,
      @RequestParam(required = false) AuditActionType actionType) {
    return ResponseEntity.ok(auditService.listAuditRecords(targetId, actionType));
  }
}
