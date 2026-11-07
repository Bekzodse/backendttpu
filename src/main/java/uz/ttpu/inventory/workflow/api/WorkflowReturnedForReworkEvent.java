package uz.ttpu.inventory.workflow.api;

import java.time.Instant;
import java.util.UUID;
import uz.ttpu.inventory.workflow.domain.WorkflowDocumentType;

public record WorkflowReturnedForReworkEvent(
        UUID instanceId,
        WorkflowDocumentType documentType,
        UUID documentId,
        String documentNumber,
        int returnedFromStepOrder,
        String reworkReason,
        Instant returnedAt
) {

    public String documentTypeName() {
        return documentType != null ? documentType.name() : "";
    }
}
