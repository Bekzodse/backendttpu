package uz.ttpu.inventory.movement.api;

import java.time.Instant;
import java.util.UUID;

public record PermitCheckpointResultDto(
    boolean mayLeave,
    String decision,
    String message,
    String qrId,
    UUID itemId,
    String inventoryNumber,
    String itemName,
    String permitNumber,
    String issuedToEmployee,
    Instant validFrom,
    Instant validUntil,
    Instant checkedAt) {}
