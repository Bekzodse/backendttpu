package uz.ttpu.inventory.references.api;

import java.time.Instant;
import java.util.UUID;

public record DepartmentDto(
        UUID id,
        String code,
        String name,
        UUID parentId,
        UUID buildingId,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
