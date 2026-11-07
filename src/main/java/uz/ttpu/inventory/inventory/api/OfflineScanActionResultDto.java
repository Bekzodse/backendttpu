package uz.ttpu.inventory.inventory.api;

import java.time.Instant;
import java.util.UUID;
import uz.ttpu.inventory.inventory.domain.ReconciliationCategory;

public record OfflineScanActionResultDto(
        UUID clientActionId,
        UUID scanActionId,
        UUID sessionId,
        UUID itemId,
        String qrCode,
        String inventoryNumber,
        UUID expectedRoomId,
        UUID scannedRoomId,
        ReconciliationCategory category,
        String categoryLabel,
        boolean duplicateIgnored,
        Instant scannedAt
) {
}
