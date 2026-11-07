package uz.ttpu.inventory.asset.api;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public record RecordDataWipeRequest(
    @NotBlank String performedByName,
    UUID performedByEmployeeId,
    @NotBlank String wipeMethod,
    String certificateNumber,
    String notes,
    Instant wipedAt) {}
