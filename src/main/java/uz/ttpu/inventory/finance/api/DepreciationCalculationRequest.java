package uz.ttpu.inventory.finance.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record DepreciationCalculationRequest(
        @NotNull UUID itemId,
        @NotBlank String inventoryNumber,
        @NotBlank String itemName,
        @NotNull @DecimalMin("0.00") BigDecimal initialValue,
        @DecimalMin("0.00") BigDecimal salvageValue,
        @Min(1) int usefulLifeYears,
        @Min(0) int elapsedYears
) {
}
