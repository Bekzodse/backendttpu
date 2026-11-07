package uz.ttpu.inventory.repair.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record VendorRepairRequest(
    @NotBlank @Size(max = 255) String vendorName,
    @Size(max = 150) String vendorContractNumber,
    @DecimalMin("0.00") BigDecimal estimatedCost,
    Instant expectedReturnAt) {}
