package uz.ttpu.inventory.asset.api;

import java.time.Instant;
import java.util.UUID;
import uz.ttpu.inventory.asset.domain.ItemEvent;
import uz.ttpu.inventory.asset.domain.ItemState;

public record ItemStateHistoryDto(
        UUID id,
        UUID itemId,
        ItemState fromState,
        ItemState toState,
        ItemEvent event,
        String documentType,
        String documentRef,
        Instant transitionedAt
) {
}
