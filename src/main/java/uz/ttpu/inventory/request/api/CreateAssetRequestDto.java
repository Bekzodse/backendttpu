package uz.ttpu.inventory.request.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateAssetRequestDto(
    @NotBlank String requestNumber,
    @NotNull UUID categoryId,
    @NotBlank String requestedItemName,
    @Min(1) int quantity,
    @NotNull @DecimalMin("0.00") BigDecimal estimatedUnitCost,
    @NotNull UUID departmentId,
    UUID buildingId,
    @NotNull UUID requesterEmployeeId,
    String justification) {}
