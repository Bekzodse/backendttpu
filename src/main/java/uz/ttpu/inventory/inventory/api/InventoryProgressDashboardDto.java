package uz.ttpu.inventory.inventory.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uz.ttpu.inventory.inventory.domain.InventorySessionStatus;

public record InventoryProgressDashboardDto(
        UUID sessionId,
        String sessionCode,
        String rectorOrderNumber,
        InventorySessionStatus status,
        int totalRooms,
        int completedRooms,
        int totalExpectedItems,
        int totalFoundItems,
        int totalNotFoundItems,
        BigDecimal overallCompletionPercentage,
        List<RoomProgressDto> rooms,
        Instant updatedAt
) {
}
