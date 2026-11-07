package uz.ttpu.inventory.repair.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ItemRepairCompletedEvent(
        UUID ticketId,
        String ticketNumber,
        UUID itemId,
        BigDecimal actualCost,
        String resolutionNotes,
        Instant completedAt
) {
}
