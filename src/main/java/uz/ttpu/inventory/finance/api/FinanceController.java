package uz.ttpu.inventory.finance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.finance.service.DepreciationService;

@RestController
@RequestMapping("/api/v1/finance/depreciation")
@RequiredArgsConstructor
@Tag(
        name = "16. Finance & Straight-Line Depreciation",
        description = "Straight-line depreciation forecast calculations ((initialValue - salvageValue) / usefulLifeYears) with multi-year schedules and NUMERIC(18,2) rounding reconciliation."
)
public class FinanceController {

    private final DepreciationService depreciationService;

    @PostMapping({"/forecast", "/calculate"})
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'RECTOR', 'VICE_RECTOR', 'RECTORATE')")
    @Operation(
            summary = "Calculate and persist a straight-line depreciation forecast",
            description = "Computes annual and monthly straight-line depreciation: (initialValue - salvageValue) / usefulLifeYears, generates a year-by-year schedule, and persists the forecast."
    )
    public DepreciationForecastDto calculateForecast(@Valid @RequestBody DepreciationCalculationRequest request) {
        return depreciationService.calculateAndSaveForecast(request);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "List all calculated depreciation forecasts",
            description = "Returns all persisted straight-line depreciation forecasts and their yearly schedules."
    )
    public List<DepreciationForecastDto> listAllForecasts() {
        return depreciationService.listAllForecasts();
    }

    @PostMapping("/items/{itemId}/forecast")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'RECTOR', 'VICE_RECTOR', 'RECTORATE')")
    @Operation(
            summary = "Calculate straight-line depreciation forecast for an existing item",
            description = "Reads the item's purchasePrice from the asset module, calculates its straight-line depreciation schedule for the given salvageValue and usefulLifeYears, and persists the forecast."
    )
    public DepreciationForecastDto calculateItemForecast(
            @PathVariable UUID itemId,
            @RequestParam(defaultValue = "0.00") BigDecimal salvageValue,
            @RequestParam(defaultValue = "5") int usefulLifeYears,
            @RequestParam(defaultValue = "0") int elapsedYears
    ) {
        return depreciationService.calculateForecastForItem(itemId, salvageValue, usefulLifeYears, elapsedYears);
    }

    @GetMapping("/items/{itemId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "List depreciation forecasts for a specific item",
            description = "Returns all historical depreciation forecast calculations for the specified item UUID."
    )
    public List<DepreciationForecastDto> listItemForecasts(@PathVariable UUID itemId) {
        return depreciationService.listForecastsForItem(itemId);
    }
}
