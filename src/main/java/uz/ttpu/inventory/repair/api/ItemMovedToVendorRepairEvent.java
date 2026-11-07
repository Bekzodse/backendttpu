package uz.ttpu.inventory.repair.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ItemMovedToVendorRepairEvent(
        UUID ticketId,
        String ticketNumber,
        UUID itemId,
        String vendorName,
        String vendorContractNumber,
        BigDecimal estimatedCost,
        Instant expectedReturnAt,
        Instant occurredAt
) {
}
