package uz.ttpu.inventory.integration.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OneCDepreciationRecordDto(
    UUID itemId,
    @NotBlank String inventoryNumber,
    @NotBlank String oneCRefId,
    @NotNull BigDecimal depreciatedAmount,
    @NotNull BigDecimal residualValue,
    @NotNull Instant periodDate) {}
