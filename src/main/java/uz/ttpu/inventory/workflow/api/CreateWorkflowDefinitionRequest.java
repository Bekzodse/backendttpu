package uz.ttpu.inventory.workflow.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import uz.ttpu.inventory.workflow.domain.WorkflowDocumentType;

public record CreateWorkflowDefinitionRequest(
    @NotBlank @Size(max = 100) String code,
    @NotBlank @Size(max = 255) String name,
    @NotNull WorkflowDocumentType documentType,
    String description,
    @NotEmpty @Valid List<WorkflowStepDefinitionRequest> steps) {}
