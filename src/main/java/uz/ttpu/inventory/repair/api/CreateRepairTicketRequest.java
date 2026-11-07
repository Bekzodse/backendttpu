package uz.ttpu.inventory.repair.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateRepairTicketRequest(
    @NotBlank @Size(max = 100) String ticketNumber,
    @NotNull UUID itemId,
    @NotNull UUID reportedByEmployeeId,
    UUID assignedTechnicianEmployeeId,
    @NotBlank String issueDescription,
    Instant expectedReturnAt) {}
