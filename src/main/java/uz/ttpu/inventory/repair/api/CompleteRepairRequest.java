package uz.ttpu.inventory.repair.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record CompleteRepairRequest(
    @DecimalMin("0.00") BigDecimal actualCost, @NotBlank String resolutionNotes) {}
