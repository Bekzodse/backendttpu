package uz.ttpu.inventory.movement.api;

import java.time.Instant;
import java.util.UUID;

public record ClearanceChecklistClosedEvent(
    UUID checklistId,
    String checklistNumber,
    UUID employeeId,
    String employeeFullName,
    Instant closedAt) {}
