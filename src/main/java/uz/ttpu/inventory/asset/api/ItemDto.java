package uz.ttpu.inventory.asset.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.common.domain.BuildingScoped;
import uz.ttpu.inventory.common.domain.DepartmentScoped;

public record ItemDto(
        UUID id,
        String inventoryNumber,
        String serialNumber,
        String qrCode,
        String name,
        String description,
        UUID categoryId,
        ItemState state,
        BigDecimal purchasePrice,
        BigDecimal residualValue,
        Instant commissionedAt,
        Instant warrantyExpiresAt,
        Instant expectedReturnAt,
        UUID buildingId,
        UUID floorId,
        UUID roomId,
        UUID departmentId,
        UUID custodianEmployeeId,
        Map<String, Object> customAttributes,
        String oneCRefId,
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

    public String stateName() {
        return state != null ? state.name() : "";
    }
}
