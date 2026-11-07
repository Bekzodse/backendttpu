package uz.ttpu.inventory.movement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MovementRejectRequest(@NotNull UUID recipientEmployeeId, @NotBlank String reason) {}
