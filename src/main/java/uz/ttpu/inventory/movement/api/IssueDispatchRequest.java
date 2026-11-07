package uz.ttpu.inventory.movement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record IssueDispatchRequest(
    UUID itemId,
    String qrCode,
    @NotBlank @Size(max = 100) String documentNumber,
    UUID toBuildingId,
    UUID toFloorId,
    @NotNull UUID toRoomId,
    UUID toDepartmentId,
    @NotNull UUID recipientEmployeeId,
    UUID warehouseDispatcherEmployeeId,
    String notes) {}
