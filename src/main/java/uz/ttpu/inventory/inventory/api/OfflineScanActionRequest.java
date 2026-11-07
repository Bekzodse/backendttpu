package uz.ttpu.inventory.inventory.api;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record OfflineScanActionRequest(
        @NotNull UUID clientActionId,
        UUID sessionId,
        @NotNull UUID scannedRoomId,
        @NotNull UUID scannedByEmployeeId,
        UUID itemId,
        String qrCode,
        String inventoryNumber,
        String itemName,
        Instant scannedAt,
        String notes
) {
}
