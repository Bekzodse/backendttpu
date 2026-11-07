package uz.ttpu.inventory.movement.api;

import java.time.Instant;
import java.util.UUID;

public record ClearanceChecklistOpenedEvent(
    UUID checklistId,
    String checklistNumber,
    UUID employeeId,
    String employeeFullName,
    int totalAssignedItems,
    Instant openedAt) {}
