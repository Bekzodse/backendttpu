package uz.ttpu.inventory.finance.api;

import java.math.BigDecimal;

public record YearlyDepreciationForecastDto(
        int yearNumber,
        BigDecimal openingBookValue,
        BigDecimal depreciationExpense,
        BigDecimal accumulatedDepreciation,
        BigDecimal closingBookValue
) {
}
