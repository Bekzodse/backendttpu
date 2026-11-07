package uz.ttpu.inventory.inventory.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ScanInventoryItemRequest(
        UUID itemId,
        String qrCode,
        @NotNull UUID scannedRoomId,
        @NotNull UUID scannedByEmployeeId,
        String notes
) {
}
