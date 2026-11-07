package uz.ttpu.inventory.references.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record FloorRequest(
        @NotNull UUID buildingId,
        @NotNull Integer floorNumber,
        @NotBlank @Size(max = 100) String name
) {
}
