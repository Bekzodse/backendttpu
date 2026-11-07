package uz.ttpu.inventory.workflow.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;
import uz.ttpu.inventory.workflow.domain.VerificationMethod;

public record WorkflowStepDefinitionRequest(
        @Min(1) int stepOrder,
        @NotBlank @Size(max = 150) String stepName,
        @NotBlank @Size(max = 64) String approverRole,
        @NotNull VerificationMethod requiredVerificationMethod,
        BigDecimal minAmountCondition,
        BigDecimal maxAmountCondition,
        Map<String, Object> conditionAttributes
) {
}
