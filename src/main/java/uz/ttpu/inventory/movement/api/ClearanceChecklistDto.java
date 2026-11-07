package uz.ttpu.inventory.movement.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uz.ttpu.inventory.movement.domain.ClearanceChecklistStatus;

public record ClearanceChecklistDto(
    UUID id,
    String checklistNumber,
    UUID employeeId,
    String employeeCode,
    String employeeFullName,
    String departmentName,
    ClearanceChecklistStatus status,
    String dismissalOrderNumber,
    String dismissalReason,
    int totalAssignedItems,
    int remainingItemsCount,
    List<String> unreturnedItemSummaries,
    Instant openedAt,
    Instant closedAt,
    UUID closedByEmployeeId,
    String notes,
    Instant createdAt) {}
