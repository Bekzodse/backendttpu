package uz.ttpu.inventory.references.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record CategoryRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 255) String name,
        UUID parentId,
        Integer depreciationYears,
        Map<String, Object> customFields
) {
}
