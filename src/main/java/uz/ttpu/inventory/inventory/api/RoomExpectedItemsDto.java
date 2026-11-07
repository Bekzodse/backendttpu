package uz.ttpu.inventory.inventory.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoomExpectedItemsDto(
        UUID sessionId,
        UUID roomId,
        int expectedCount,
        int foundCount,
        int notFoundCount,
        boolean completed,
        Instant completedAt,
        List<InventoryExpectedItemDto> expectedItems
) {
}
