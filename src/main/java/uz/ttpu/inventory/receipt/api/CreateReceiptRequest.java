package uz.ttpu.inventory.receipt.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateReceiptRequest(
        @NotBlank @Size(max = 100) String receiptNumber,
        @NotBlank @Size(max = 150) String contractNumber,
        Instant contractDate,
        @NotBlank @Size(max = 150) String invoiceNumber,
        Instant invoiceDate,
        @NotBlank @Size(max = 255) String supplierName,
        @Size(max = 50) String supplierTin,
        @NotNull Instant receivedAt,
        UUID warehouseBuildingId,
        UUID receivedByEmployeeId,
        String notes,
        @NotEmpty @Valid List<ReceiptLineRequest> lines
) {
}
