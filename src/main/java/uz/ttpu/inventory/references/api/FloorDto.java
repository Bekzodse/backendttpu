package uz.ttpu.inventory.references.api;

import java.time.Instant;
import java.util.UUID;

public record FloorDto(
        UUID id,
        UUID buildingId,
        Integer floorNumber,
        String name,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
