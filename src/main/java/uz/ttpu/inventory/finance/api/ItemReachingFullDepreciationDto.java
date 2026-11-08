package uz.ttpu.inventory.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ItemReachingFullDepreciationDto(
    UUID itemId,
    String inventoryNumber,
    String itemName,
    BigDecimal initialValue,
    BigDecimal residualValue,
    BigDecimal salvageValue,
    int usefulLifeYears,
    BigDecimal annualDepreciation,
    BigDecimal monthlyDepreciation,
    int monthsRemaining,
    LocalDate projectedFullDepreciationDate,
    boolean fullyDepreciated) {}
