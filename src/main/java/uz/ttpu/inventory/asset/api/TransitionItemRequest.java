package uz.ttpu.inventory.asset.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uz.ttpu.inventory.asset.domain.ItemEvent;

public record TransitionItemRequest(
        @NotNull ItemEvent event,
        @NotBlank String documentReference
) {
}
