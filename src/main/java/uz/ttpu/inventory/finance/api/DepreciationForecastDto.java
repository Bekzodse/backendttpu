package uz.ttpu.inventory.finance.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DepreciationForecastDto(
    UUID id,
    UUID itemId,
    String inventoryNumber,
    String itemName,
    BigDecimal initialValue,
    BigDecimal salvageValue,
    int usefulLifeYears,
    BigDecimal depreciableBase,
    BigDecimal annualDepreciation,
    BigDecimal monthlyDepreciation,
    BigDecimal accumulatedDepreciation,
    BigDecimal netBookValue,
    List<YearlyDepreciationForecastDto> yearlySchedule,
    Instant calculatedAt) {}
