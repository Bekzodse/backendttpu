package uz.ttpu.inventory.workflow.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import uz.ttpu.inventory.workflow.domain.WorkflowDocumentType;
import uz.ttpu.inventory.workflow.domain.WorkflowState;

public record WorkflowInstanceDto(
    UUID id,
    UUID definitionId,
    String definitionCode,
    WorkflowDocumentType documentType,
    UUID documentId,
    String documentNumber,
    BigDecimal amount,
    Map<String, Object> contextAttributes,
    WorkflowState state,
    Integer currentStepOrder,
    UUID initiatedByUserId,
    Instant submittedAt,
    Instant completedAt,
    String rejectionReason,
    String reworkReason,
    List<WorkflowStepDecisionDto> decisions,
    Instant createdAt,
    Instant updatedAt) {}
