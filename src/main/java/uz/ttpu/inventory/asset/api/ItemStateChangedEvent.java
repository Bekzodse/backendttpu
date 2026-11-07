package uz.ttpu.inventory.asset.api;

import java.time.Instant;
import java.util.UUID;
import uz.ttpu.inventory.asset.domain.ItemEvent;
import uz.ttpu.inventory.asset.domain.ItemState;

public record ItemStateChangedEvent(
    UUID itemId,
    String inventoryNumber,
    ItemState fromState,
    ItemState toState,
    ItemEvent event,
    String documentType,
    String documentRef,
    Instant occurredAt) {}
