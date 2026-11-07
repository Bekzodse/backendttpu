package uz.ttpu.inventory.workflow.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record StartWorkflowInstanceRequest(
    @NotBlank String definitionCode,
    @NotNull UUID documentId,
    String documentNumber,
    BigDecimal amount,
    Map<String, Object> contextAttributes,
    UUID initiatedByUserId,
    boolean submitImmediately) {}
