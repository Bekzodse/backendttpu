package uz.ttpu.inventory.receipt.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uz.ttpu.inventory.receipt.domain.ReceiptStatus;

public record ReceiptDto(
    UUID id,
    String receiptNumber,
    String contractNumber,
    Instant contractDate,
    String invoiceNumber,
    Instant invoiceDate,
    String supplierName,
    String supplierTin,
    ReceiptStatus status,
    Instant receivedAt,
    UUID warehouseBuildingId,
    UUID receivedByEmployeeId,
    String notes,
    List<ReceiptLineDto> lines,
    Instant createdAt,
    Instant updatedAt) {}
