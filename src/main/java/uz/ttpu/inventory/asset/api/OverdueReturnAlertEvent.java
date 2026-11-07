package uz.ttpu.inventory.asset.api;

import java.time.Instant;
import java.util.UUID;
import uz.ttpu.inventory.asset.domain.ItemState;

public record OverdueReturnAlertEvent(
        UUID itemId,
        String inventoryNumber,
        String itemName,
        ItemState currentState,
        Instant expectedReturnAt,
        UUID custodianEmployeeId,
        Instant detectedAt
) {
}
