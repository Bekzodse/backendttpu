package uz.ttpu.inventory.inventory.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import uz.ttpu.inventory.inventory.domain.InventoryLineStatus;

public record InventoryExpectedItemDto(
        UUID id,
        UUID sessionId,
        UUID roomId,
        UUID itemId,
        String inventoryNumber,
        String qrCode,
        String itemName,
        UUID custodianEmployeeId,
        BigDecimal bookValue,
        InventoryLineStatus lineStatus,
        UUID scannedRoomId,
        UUID scannedByEmployeeId,
        Instant scannedAt,
        String notes
) {
}
