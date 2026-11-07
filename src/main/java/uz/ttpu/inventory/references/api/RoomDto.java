package uz.ttpu.inventory.references.api;

import java.time.Instant;
import java.util.UUID;

public record RoomDto(
    UUID id,
    UUID floorId,
    UUID departmentId,
    String roomNumber,
    String name,
    Integer capacity,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {}
