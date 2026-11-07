package uz.ttpu.inventory.receipt.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ReceiptLineRequest(
    @NotBlank @Size(max = 255) String name,
    @NotNull UUID categoryId,
    @NotBlank @Size(max = 150) String serialNumber,
    @NotNull @DecimalMin("0.00") BigDecimal priceWithoutVat,
    @Min(1) Integer quantity) {}
