package uz.ttpu.inventory.asset.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CreateItemRequest(
    @NotBlank @Size(max = 100) String inventoryNumber,
    @Size(max = 150) String serialNumber,
    @Size(max = 255) String qrCode,
    @NotBlank @Size(max = 255) String name,
    String description,
    @NotNull UUID categoryId,
    @NotNull @DecimalMin("0.00") BigDecimal purchasePrice,
    @NotNull @DecimalMin("0.00") BigDecimal residualValue,
    Instant commissionedAt,
    Instant warrantyExpiresAt,
    Instant expectedReturnAt,
    UUID buildingId,
    UUID floorId,
    UUID roomId,
    UUID departmentId,
    UUID custodianEmployeeId,
    Map<String, Object> customAttributes,
    @Size(max = 100) String oneCRefId) {}
