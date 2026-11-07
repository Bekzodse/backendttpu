package uz.ttpu.inventory.finance.api;

import java.time.Instant;
import java.util.UUID;

public record WriteOffResultDto(
    UUID itemId,
    String inventoryNumber,
    String documentNumber,
    boolean dataWipeVerified,
    Instant completedAt) {}
