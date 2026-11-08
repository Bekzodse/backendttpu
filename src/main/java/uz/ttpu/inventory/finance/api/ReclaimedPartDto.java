package uz.ttpu.inventory.finance.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReclaimedPartDto(
    UUID id,
    UUID writtenOffItemId,
    UUID reclaimedItemId,
    String partName,
    String serialNumber,
    String inventoryNumber,
    BigDecimal estimatedValue,
    UUID warehouseBuildingId,
    UUID warehouseRoomId,
    String warehouseStatus,
    Instant reclaimedAt,
    String reclaimedByUsername,
    String notes) {}
