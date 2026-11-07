package uz.ttpu.inventory.asset.api;

import java.time.Instant;
import java.util.UUID;

public record WarrantyExpiringAlertEvent(
        UUID itemId,
        String inventoryNumber,
        String itemName,
        Instant warrantyExpiresAt,
        long daysRemaining,
        UUID custodianEmployeeId,
        Instant detectedAt
) {
}
