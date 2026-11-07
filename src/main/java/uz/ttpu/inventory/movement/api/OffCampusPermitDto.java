package uz.ttpu.inventory.movement.api;

import java.time.Instant;
import java.util.UUID;
import uz.ttpu.inventory.movement.domain.PermitStatus;

public record OffCampusPermitDto(
    UUID id,
    String permitNumber,
    UUID itemId,
    String qrCode,
    String inventoryNumber,
    String itemName,
    UUID employeeId,
    String employeeFullName,
    String purpose,
    Instant validFrom,
    Instant validUntil,
    PermitStatus status,
    String approvedBy,
    Instant revokedAt,
    String revocationReason,
    Instant createdAt) {}
