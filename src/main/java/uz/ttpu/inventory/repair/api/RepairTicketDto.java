package uz.ttpu.inventory.repair.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import uz.ttpu.inventory.repair.domain.RepairTicketState;

public record RepairTicketDto(
        UUID id,
        String ticketNumber,
        UUID itemId,
        UUID reportedByEmployeeId,
        UUID assignedTechnicianEmployeeId,
        RepairTicketState state,
        String issueDescription,
        String vendorName,
        String vendorContractNumber,
        BigDecimal estimatedCost,
        BigDecimal actualCost,
        Instant expectedReturnAt,
        Instant completedAt,
        Instant closedAt,
        String resolutionNotes,
        Instant createdAt,
        Instant updatedAt
) {
}
