package uz.ttpu.inventory.workflow.api;

import java.time.Instant;
import java.util.UUID;
import uz.ttpu.inventory.workflow.domain.WorkflowDocumentType;
import uz.ttpu.inventory.workflow.domain.WorkflowState;

public record WorkflowCompletedEvent(
        UUID instanceId,
        WorkflowDocumentType documentType,
        UUID documentId,
        String documentNumber,
        WorkflowState finalState,
        String reason,
        Instant completedAt
) {

    public String documentTypeName() {
        return documentType != null ? documentType.name() : "";
    }

    public String finalStateName() {
        return finalState != null ? finalState.name() : "";
    }

    public boolean isWriteOffDocument() {
        return documentType == WorkflowDocumentType.WRITE_OFF;
    }

    public boolean isServiceRequestDocument() {
        return documentType == WorkflowDocumentType.SERVICE_REQUEST;
    }

    public boolean isApproved() {
        return finalState == WorkflowState.APPROVED;
    }

    public boolean isRejected() {
        return finalState == WorkflowState.REJECTED;
    }
}
