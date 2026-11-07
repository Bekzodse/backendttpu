package uz.ttpu.inventory.workflow.api;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import uz.ttpu.inventory.workflow.domain.VerificationMethod;

public record WorkflowStepDefinitionDto(
        UUID id,
        int stepOrder,
        String stepName,
        String approverRole,
        VerificationMethod requiredVerificationMethod,
        BigDecimal minAmountCondition,
        BigDecimal maxAmountCondition,
        Map<String, Object> conditionAttributes
) {
}
