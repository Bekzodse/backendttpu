package uz.ttpu.inventory.references.api;

import java.time.Instant;
import java.util.UUID;

public record BuildingDto(
    UUID id,
    String code,
    String name,
    String address,
    String description,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {}
