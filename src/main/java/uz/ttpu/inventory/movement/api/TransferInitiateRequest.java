package uz.ttpu.inventory.movement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import uz.ttpu.inventory.movement.domain.MovementType;

public record TransferInitiateRequest(
    UUID itemId,
    String qrCode,
    @NotBlank @Size(max = 100) String documentNumber,
    @NotNull MovementType transferType,
    UUID toBuildingId,
    UUID toFloorId,
    @NotNull UUID toRoomId,
    UUID toDepartmentId,
    @NotNull UUID receivingEmployeeId,
    UUID initiatingEmployeeId,
    String notes) {}
