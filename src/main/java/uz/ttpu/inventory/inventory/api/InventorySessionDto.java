package uz.ttpu.inventory.inventory.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uz.ttpu.inventory.inventory.domain.InventorySessionStatus;

public record InventorySessionDto(
    UUID id,
    String sessionCode,
    String rectorOrderNumber,
    Instant rectorOrderDate,
    Instant sessionDate,
    InventorySessionStatus status,
    UUID chairmanEmployeeId,
    List<UUID> commissionMemberIds,
    List<UUID> roomIds,
    Instant startedAt,
    Instant completedAt,
    String notes,
    Instant createdAt,
    Instant updatedAt) {}
