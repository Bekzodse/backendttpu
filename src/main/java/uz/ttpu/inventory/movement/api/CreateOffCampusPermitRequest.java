package uz.ttpu.inventory.movement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateOffCampusPermitRequest(
    @NotNull UUID itemId,
    String qrCode,
    @NotNull UUID employeeId,
    String employeeFullName,
    @NotBlank String purpose,
    @NotNull Instant validFrom,
    @NotNull Instant validUntil,
    String approvedBy) {}
