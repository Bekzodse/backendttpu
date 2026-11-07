package uz.ttpu.inventory.receipt.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReceiptLineDto(
        UUID id,
        UUID receiptId,
        String name,
        UUID categoryId,
        String serialNumber,
        BigDecimal priceWithoutVat,
        Integer quantity,
        Instant createdAt,
        Instant updatedAt
) {
}
