package uz.ttpu.inventory.references.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CategoryDto(
        UUID id,
        String code,
        String name,
        UUID parentId,
        Integer depreciationYears,
        Map<String, Object> customFields,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
