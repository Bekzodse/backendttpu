package uz.ttpu.inventory.asset.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetLogisticsApi {

    ItemDto resolveItemByIdOrQr(UUID itemId, String qrCode);

    Optional<ItemDto> findItemByIdOrQrOptional(UUID itemId, String qrCode);

    List<ItemDto> listAllItemsForReporting();

    List<ItemDto> findExpectedItemsForRooms(Collection<UUID> roomIds);

    ItemDto markItemMissingDuringInventory(UUID itemId, String sessionCode);

    ItemDto markItemFoundDuringInventory(UUID itemId, UUID scannedRoomId, String sessionCode);

    ItemDto applyOneCDepreciation(
            UUID itemId,
            String inventoryNumber,
            String oneCRefId,
            BigDecimal newResidualValue
    );

    ItemDto dispatchForIssue(UUID itemId, String documentNumber);

    ItemDto confirmIssue(
            UUID itemId,
            UUID toBuildingId,
            UUID toFloorId,
            UUID toRoomId,
            UUID toDepartmentId,
            UUID toEmployeeId,
            String documentNumber
    );

    ItemDto rejectIssue(UUID itemId, String documentNumber);

    ItemDto initiateTransfer(UUID itemId, String documentNumber);

    ItemDto confirmTransfer(
            UUID itemId,
            UUID toBuildingId,
            UUID toFloorId,
            UUID toRoomId,
            UUID toDepartmentId,
            UUID toEmployeeId,
            String documentNumber
    );

    ItemDto rejectTransfer(UUID itemId, String documentNumber);

    ItemDto upsertPilotMigratedItem(PilotItemUpsertRequest request);
}
