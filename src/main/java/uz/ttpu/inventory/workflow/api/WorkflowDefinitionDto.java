package uz.ttpu.inventory.workflow.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uz.ttpu.inventory.workflow.domain.WorkflowDocumentType;

public record WorkflowDefinitionDto(
        UUID id,
        String code,
        String name,
        WorkflowDocumentType documentType,
        String description,
        boolean active,
        List<WorkflowStepDefinitionDto> steps,
        Instant createdAt,
        Instant updatedAt
) {
}
