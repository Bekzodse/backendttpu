package uz.ttpu.inventory.repair.api;

import java.time.Instant;
import java.util.UUID;
import uz.ttpu.inventory.repair.domain.RepairTicketState;

public record OverdueRepairTicketAlertEvent(
        UUID ticketId,
        String ticketNumber,
        UUID itemId,
        RepairTicketState state,
        String vendorName,
        Instant expectedReturnAt,
        Instant detectedAt
) {
}
