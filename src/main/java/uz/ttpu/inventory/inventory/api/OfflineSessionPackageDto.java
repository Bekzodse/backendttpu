package uz.ttpu.inventory.inventory.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uz.ttpu.inventory.inventory.domain.InventorySessionStatus;

public record OfflineSessionPackageDto(
    UUID sessionId,
    String sessionCode,
    String rectorOrderNumber,
    Instant rectorOrderDate,
    Instant sessionDate,
    InventorySessionStatus status,
    long version,
    Instant generatedAt,
    List<RoomExpectedItemsDto> rooms,
    List<InventoryExpectedItemDto> expectedItems) {}
