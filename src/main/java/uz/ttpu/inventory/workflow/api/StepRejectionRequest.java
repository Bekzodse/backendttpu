package uz.ttpu.inventory.workflow.api;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record StepRejectionRequest(
    @NotBlank(message = "Rejection reason is required") String reason,
    UUID decidedByUserId,
    String decidedByUsername) {}
