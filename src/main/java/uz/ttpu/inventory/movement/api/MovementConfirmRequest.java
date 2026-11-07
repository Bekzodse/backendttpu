package uz.ttpu.inventory.movement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record MovementConfirmRequest(
    @NotNull UUID recipientEmployeeId,
    @NotBlank @Pattern(regexp = "^\\d{6}$", message = "TOTP code must be 6 digits") String totpCode,
    String comment) {}
