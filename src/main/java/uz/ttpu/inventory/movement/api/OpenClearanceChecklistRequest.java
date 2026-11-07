package uz.ttpu.inventory.movement.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OpenClearanceChecklistRequest(
    @NotNull UUID employeeId, String dismissalOrderNumber, String dismissalReason, String notes) {}
