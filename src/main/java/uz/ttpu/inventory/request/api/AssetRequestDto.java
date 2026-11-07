package uz.ttpu.inventory.request.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import uz.ttpu.inventory.common.domain.BuildingScoped;
import uz.ttpu.inventory.common.domain.DepartmentScoped;
import uz.ttpu.inventory.request.domain.AssetRequestStatus;

public record AssetRequestDto(
        UUID id,
        String requestNumber,
        UUID categoryId,
        String requestedItemName,
        int quantity,
        BigDecimal estimatedUnitCost,
        BigDecimal totalEstimatedCost,
        UUID departmentId,
        UUID buildingId,
        UUID requesterEmployeeId,
        String justification,
        AssetRequestStatus status,
        UUID fulfilledItemId,
        Instant submittedAt,
        Instant resolvedAt,
        String resolvedByUsername,
        Instant createdAt,
        Instant updatedAt
) implements DepartmentScoped, BuildingScoped {

    @Override
    public UUID getDepartmentId() {
        return departmentId;
    }

    @Override
    public UUID getBuildingId() {
        return buildingId;
    }
}
