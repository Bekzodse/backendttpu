package uz.ttpu.inventory.finance.api;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.UUID;

public record ReclaimablePartRequest(
    @NotBlank String partName,
    String serialNumber,
    String inventoryNumber,
    BigDecimal estimatedValue,
    UUID warehouseBuildingId,
    UUID warehouseRoomId,
    String notes) {}
