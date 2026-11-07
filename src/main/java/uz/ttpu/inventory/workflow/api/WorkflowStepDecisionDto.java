package uz.ttpu.inventory.workflow.api;

import java.time.Instant;
import java.util.UUID;
import uz.ttpu.inventory.workflow.domain.VerificationMethod;
import uz.ttpu.inventory.workflow.domain.WorkflowDecisionType;

public record WorkflowStepDecisionDto(
        UUID id,
        int stepOrder,
        String stepName,
        String approverRole,
        WorkflowDecisionType decision,
        VerificationMethod verificationMethod,
        String signaturePayload,
        String reason,
        UUID decidedByUserId,
        String decidedByUsername,
        Instant decidedAt
) {
}
