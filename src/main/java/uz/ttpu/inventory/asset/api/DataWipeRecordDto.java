package uz.ttpu.inventory.asset.api;

import java.time.Instant;
import java.util.UUID;

public record DataWipeRecordDto(
    UUID id,
    UUID itemId,
    String performedByName,
    UUID performedByEmployeeId,
    String wipeMethod,
    String certificateNumber,
    String notes,
    Instant wipedAt,
    Instant createdAt) {}
