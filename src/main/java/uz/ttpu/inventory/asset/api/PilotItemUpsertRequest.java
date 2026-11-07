package uz.ttpu.inventory.asset.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PilotItemUpsertRequest(
    String inventoryNumber,
    String name,
    String serialNumber,
    String model,
    UUID categoryId,
    UUID buildingId,
    UUID floorId,
    UUID roomId,
    String roomLabel,
    UUID departmentId,
    UUID custodianEmployeeId,
    String responsiblePersonLabel,
    BigDecimal purchasePrice,
    BigDecimal residualValue,
    Instant commissionedAt,
    String oneCRefId,
    Map<String, Object> customAttributes) {}
