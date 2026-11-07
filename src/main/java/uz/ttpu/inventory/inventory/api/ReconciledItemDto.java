package uz.ttpu.inventory.inventory.api;

import java.time.Instant;
import java.util.UUID;
import uz.ttpu.inventory.inventory.domain.ReconciliationCategory;

public record ReconciledItemDto(
    UUID itemId,
    String inventoryNumber,
    String qrCode,
    String itemName,
    UUID expectedRoomId,
    UUID scannedRoomId,
    UUID scannedByEmployeeId,
    Instant scannedAt,
    ReconciliationCategory category,
    String categoryLabel,
    boolean movedToQidiruvda) {}
