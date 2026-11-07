package uz.ttpu.inventory.movement.api;

import java.util.UUID;

public record CloseClearanceChecklistRequest(UUID closedByEmployeeId, String notes) {}
