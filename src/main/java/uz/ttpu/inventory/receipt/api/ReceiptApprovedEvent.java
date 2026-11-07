package uz.ttpu.inventory.receipt.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReceiptApprovedEvent(
        UUID receiptId,
        String receiptNumber,
        UUID warehouseBuildingId,
        Instant approvedAt,
        List<ApprovedReceiptLineItem> lines
) {
    public record ApprovedReceiptLineItem(
            UUID receiptLineId,
            String name,
            UUID categoryId,
            String serialNumber,
            BigDecimal priceWithoutVat,
            int quantity
    ) {
    }
}
