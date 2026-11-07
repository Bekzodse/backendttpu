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
    boolean replacementRecommended,
    String replacementRecommendationReason,
    Instant expectedReturnAt,
    Instant completedAt,
    Instant closedAt,
    String resolutionNotes,
    Instant createdAt,
    Instant updatedAt) {

  public RepairTicketDto(
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
      Instant updatedAt) {
    this(
        id,
        ticketNumber,
        itemId,
        reportedByEmployeeId,
        assignedTechnicianEmployeeId,
        state,
        issueDescription,
        vendorName,
        vendorContractNumber,
        estimatedCost,
        actualCost,
        false,
        null,
        expectedReturnAt,
        completedAt,
        closedAt,
        resolutionNotes,
        createdAt,
        updatedAt);
  }
}
