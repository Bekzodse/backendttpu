package uz.ttpu.inventory.report.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record HandoverActPrintRequest(
    UUID operationId,
    String documentNumber,
    String movementType,
    String eventType,
    UUID itemId,
    String inventoryNumber,
    String qrCode,
    String itemName,
    String serialNumber,
    String itemState,
    BigDecimal bookValue,
    UUID fromBuildingId,
    UUID toBuildingId,
    UUID fromRoomId,
    UUID toRoomId,
    UUID fromDepartmentId,
    UUID toDepartmentId,
    UUID fromEmployeeId,
    UUID toEmployeeId,
    UUID actorEmployeeId,
    boolean totpVerified,
    String reason,
    Instant recordedAt) {}
