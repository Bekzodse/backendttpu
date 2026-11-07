package uz.ttpu.inventory.workflow.api;

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
import uz.ttpu.inventory.workflow.service.WorkflowService;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Tag(
    name = "07. Generic Workflow Approval Engine",
    description =
        "Multi-step conditional approval workflows for SERVICE_REQUEST, TRANSFER, and WRITE_OFF"
            + " documents with E-IMZO PKCS#7 digital signature or 2FA TOTP step verification.")
public class WorkflowController {

  private final WorkflowService workflowService;

  @GetMapping("/definitions")
  @Operation(
      summary = "List workflow definitions",
      description =
          "Returns all configured workflow definitions along with their ordered approval steps,"
              + " amount thresholds, and verification requirements.")
  public ResponseEntity<List<WorkflowDefinitionDto>> listDefinitions() {
    return ResponseEntity.ok(workflowService.listDefinitions());
  }

  @PostMapping("/definitions")
  @Operation(
      summary = "Create a new workflow definition",
      description =
          "Defines a multi-step approval workflow for a document type (SERVICE_REQUEST, TRANSFER,"
              + " WRITE_OFF) with ordered steps, min/max amount conditions, JSONB attribute"
              + " conditions, and required verification method (TOTP, E_IMZO, EITHER).")
  public ResponseEntity<WorkflowDefinitionDto> createDefinition(
      @Valid @RequestBody CreateWorkflowDefinitionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(workflowService.createDefinition(request));
  }

  @GetMapping("/instances/{id}")
  @Operation(
      summary = "Get workflow instance by ID",
      description =
          "Retrieves the current state (DRAFT, IN_AGREEMENT, APPROVED, REJECTED), active step"
              + " order, and step decision history of a workflow instance.")
  public ResponseEntity<WorkflowInstanceDto> getInstance(@PathVariable UUID id) {
    return ResponseEntity.ok(workflowService.getInstance(id));
  }

  @PostMapping("/instances")
  @Operation(
      summary = "Start a new workflow instance",
      description =
          "Instantiates a workflow for a target document (and optionally submits it immediately"
              + " into IN_AGREEMENT state).")
  public ResponseEntity<WorkflowInstanceDto> startInstance(
      @Valid @RequestBody StartWorkflowInstanceRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(workflowService.startInstance(request));
  }

  @PostMapping("/instances/{id}/submit")
  @Operation(
      summary = "Submit a DRAFT workflow instance for agreement",
      description =
          "Evaluates step conditions against the document amount/context and transitions the"
              + " instance from DRAFT to IN_AGREEMENT at the first applicable step.")
  public ResponseEntity<WorkflowInstanceDto> submitInstance(@PathVariable UUID id) {
    return ResponseEntity.ok(workflowService.submitInstance(id));
  }

  @PostMapping("/instances/{id}/approve")
  @Operation(
      summary = "Approve the current workflow step via E-IMZO or TOTP",
      description =
          "Verifies the step's required verification method (6-digit TOTP code or E-IMZO PKCS#7"
              + " signature) and advances to the next applicable step or completes the workflow as"
              + " APPROVED (publishing WorkflowCompletedEvent).")
  public ResponseEntity<WorkflowInstanceDto> approveStep(
      @PathVariable UUID id, @Valid @RequestBody StepApprovalRequest request) {
    return ResponseEntity.ok(workflowService.approveStep(id, request));
  }

  @PostMapping("/instances/{id}/reject")
  @Operation(
      summary = "Reject the current workflow step with a mandatory reason",
      description =
          "Terminates the workflow instance in REJECTED state with the provided reason and"
              + " publishes WorkflowCompletedEvent.")
  public ResponseEntity<WorkflowInstanceDto> rejectStep(
      @PathVariable UUID id, @Valid @RequestBody StepRejectionRequest request) {
    return ResponseEntity.ok(workflowService.rejectStep(id, request));
  }

  @PostMapping("/instances/{id}/rework")
  @Operation(
      summary = "Return the workflow instance to DRAFT for rework",
      description =
          "Returns an IN_AGREEMENT workflow instance back to DRAFT state with a mandatory rework"
              + " reason and publishes WorkflowReturnedForReworkEvent.")
  public ResponseEntity<WorkflowInstanceDto> returnStepForRework(
      @PathVariable UUID id, @Valid @RequestBody StepReworkRequest request) {
    return ResponseEntity.ok(workflowService.returnStepForRework(id, request));
  }
}
