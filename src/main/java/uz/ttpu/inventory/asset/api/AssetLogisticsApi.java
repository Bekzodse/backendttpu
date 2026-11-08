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
      UUID itemId, String inventoryNumber, String oneCRefId, BigDecimal newResidualValue);

  ItemDto dispatchForIssue(UUID itemId, String documentNumber);

  ItemDto confirmIssue(
      UUID itemId,
      UUID toBuildingId,
      UUID toFloorId,
      UUID toRoomId,
      UUID toDepartmentId,
      UUID toEmployeeId,
      String documentNumber);

  ItemDto rejectIssue(UUID itemId, String documentNumber);

  ItemDto initiateTransfer(UUID itemId, String documentNumber);

  ItemDto confirmTransfer(
      UUID itemId,
      UUID toBuildingId,
      UUID toFloorId,
      UUID toRoomId,
      UUID toDepartmentId,
      UUID toEmployeeId,
      String documentNumber);

  ItemDto rejectTransfer(UUID itemId, String documentNumber);

  boolean existsByInventoryNumber(String inventoryNumber);

  boolean existsBySerialNumber(String serialNumber);

  ItemDto upsertPilotMigratedItem(PilotItemUpsertRequest request);

  List<ItemDto> findItemsByResponsibleEmployee(UUID employeeId);

  long countAssignedItems(UUID employeeId);

  void setReplacementRecommended(UUID itemId, boolean recommended);

  boolean isDataWipeRequired(UUID itemId);

  boolean hasCertifiedDataWipe(UUID itemId);

  DataWipeRecordDto recordDataWipe(UUID itemId, RecordDataWipeRequest request);

  Optional<DataWipeRecordDto> getLatestDataWipe(UUID itemId);

  ItemDto completeWriteOff(UUID itemId, String documentNumber);

  ItemDto receiveReclaimedPart(
      UUID parentItemId,
      String partName,
      String serialNumber,
      String inventoryNumber,
      BigDecimal estimatedValue,
      UUID warehouseBuildingId,
      UUID warehouseRoomId,
      String notes,
      String performedByUsername);

  List<ItemDto> findReclaimedPartsByParentItem(UUID parentItemId);
}
