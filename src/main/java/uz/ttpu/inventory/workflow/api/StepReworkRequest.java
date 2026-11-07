package uz.ttpu.inventory.workflow.api;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record StepReworkRequest(
        @NotBlank(message = "Rework reason is required")
        String reason,
        UUID decidedByUserId,
        String decidedByUsername
) {
}
