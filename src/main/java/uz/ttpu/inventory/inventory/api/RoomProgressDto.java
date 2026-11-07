package uz.ttpu.inventory.inventory.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RoomProgressDto(
    UUID roomId,
    int expectedCount,
    int foundCount,
    int notFoundCount,
    BigDecimal completionPercentage,
    boolean completed,
    Instant completedAt) {}
