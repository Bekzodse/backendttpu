package uz.ttpu.inventory.report.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryReportRowDto(
        String inventoryNumber,
        String qrCode,
        String name,
        String serialNumber,
        String state,
        BigDecimal purchasePrice,
        BigDecimal residualValue,
        UUID buildingId,
        UUID roomId,
        UUID departmentId,
        UUID custodianEmployeeId,
        Instant commissionedAt
) {
}
