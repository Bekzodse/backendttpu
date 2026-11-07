package uz.ttpu.inventory.references.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RoomRequest(
    @NotNull UUID floorId,
    UUID departmentId,
    @NotBlank @Size(max = 50) String roomNumber,
    @Size(max = 255) String name,
    Integer capacity) {}
