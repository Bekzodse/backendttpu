package uz.ttpu.inventory.inventory.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uz.ttpu.inventory.inventory.domain.InventorySessionStatus;

public record InventoryReconciliationReportDto(
        UUID sessionId,
        String sessionCode,
        InventorySessionStatus sessionStatus,
        long packageVersion,
        int joyidaCount,
        int boshqaXonadaTopildiCount,
        int topilmadiCount,
        int ortiqchaCount,
        List<ReconciledItemDto> joyida,
        List<ReconciledItemDto> boshqaXonadaTopildi,
        List<ReconciledItemDto> topilmadi,
        List<ReconciledItemDto> ortiqcha,
        Instant reconciledAt
) {
}
