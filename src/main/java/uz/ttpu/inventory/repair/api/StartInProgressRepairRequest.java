package uz.ttpu.inventory.repair.api;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record StartInProgressRepairRequest(
    @NotNull UUID assignedTechnicianEmployeeId, Instant expectedReturnAt) {}
