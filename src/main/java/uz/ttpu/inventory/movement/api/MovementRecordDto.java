package uz.ttpu.inventory.movement.api;

import java.time.Instant;
import java.util.UUID;
import uz.ttpu.inventory.movement.domain.MovementEventType;
import uz.ttpu.inventory.movement.domain.MovementType;

public record MovementRecordDto(
        UUID id,
        UUID operationId,
        String documentNumber,
        MovementType movementType,
        MovementEventType eventType,
        UUID itemId,
        UUID fromBuildingId,
        UUID toBuildingId,
        UUID fromFloorId,
        UUID toFloorId,
        UUID fromRoomId,
        UUID toRoomId,
        UUID fromDepartmentId,
        UUID toDepartmentId,
        UUID fromEmployeeId,
        UUID toEmployeeId,
        UUID actorEmployeeId,
        boolean totpVerified,
        String reason,
        Instant recordedAt
) {
}
