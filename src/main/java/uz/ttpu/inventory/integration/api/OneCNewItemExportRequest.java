package uz.ttpu.inventory.integration.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OneCNewItemExportRequest(
    @NotNull UUID itemId,
    @NotBlank String inventoryNumber,
    @NotBlank String name,
    String serialNumber,
    UUID categoryId,
    @NotNull BigDecimal purchasePrice,
    Instant commissionedAt,
    UUID departmentId,
    UUID custodianEmployeeId) {}
