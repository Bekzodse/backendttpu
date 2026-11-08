package uz.ttpu.inventory.finance.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.DataWipeRecordDto;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.asset.api.RecordDataWipeRequest;
import uz.ttpu.inventory.common.domain.DataWipeRequiredException;
import uz.ttpu.inventory.finance.api.ReclaimablePartRequest;
import uz.ttpu.inventory.finance.api.ReclaimedPartDto;
import uz.ttpu.inventory.finance.api.WriteOffCompletionRequest;
import uz.ttpu.inventory.finance.api.WriteOffResultDto;
import uz.ttpu.inventory.finance.domain.WriteOffReclaimedPart;
import uz.ttpu.inventory.finance.repository.WriteOffReclaimedPartRepository;

@Service
@RequiredArgsConstructor
public class FinanceWriteOffService {

  private final AssetLogisticsApi assetLogisticsApi;
  private final WriteOffReclaimedPartRepository writeOffReclaimedPartRepository;

  @Transactional
  public WriteOffResultDto completeWriteOff(UUID itemId, WriteOffCompletionRequest request) {
    ItemDto item = assetLogisticsApi.resolveItemByIdOrQr(itemId, null);

    boolean dataWipeRequired = assetLogisticsApi.isDataWipeRequired(itemId);
    boolean hasExistingWipe = assetLogisticsApi.hasCertifiedDataWipe(itemId);

    if (dataWipeRequired) {
      boolean hasInlineWipeData =
          request.performerName() != null
              && !request.performerName().isBlank()
              && request.wipeMethod() != null
              && !request.wipeMethod().isBlank();

      if (hasInlineWipeData) {
        assetLogisticsApi.recordDataWipe(
            itemId,
            new RecordDataWipeRequest(
                request.performerName().trim(),
                null,
                request.wipeMethod().trim(),
                "WO-CERT-" + request.documentNumber(),
                request.reason(),
                Instant.now()));
        hasExistingWipe = true;
      } else if (!hasExistingWipe) {
        throw new DataWipeRequiredException(itemId, item.inventoryNumber(), item.name());
      }
    }

    ItemDto completed = assetLogisticsApi.completeWriteOff(itemId, request.documentNumber());

    List<ReclaimedPartDto> reclaimedParts = new ArrayList<>();
    if (request.usableParts() != null && !request.usableParts().isEmpty()) {
      for (ReclaimablePartRequest partReq : request.usableParts()) {
        reclaimedParts.add(reclaimPart(itemId, partReq, request.performerName()));
      }
    }

    return new WriteOffResultDto(
        completed.id(),
        completed.inventoryNumber(),
        request.documentNumber(),
        hasExistingWipe,
        reclaimedParts,
        Instant.now());
  }

  @Transactional
  public ReclaimedPartDto reclaimPart(
      UUID writtenOffItemId, ReclaimablePartRequest request, String username) {
    ItemDto parentItem = assetLogisticsApi.resolveItemByIdOrQr(writtenOffItemId, null);

    String actor =
        (username != null && !username.isBlank()) ? username.trim() : "authorized_accountant";

    ItemDto reclaimedItem =
        assetLogisticsApi.receiveReclaimedPart(
            parentItem.id(),
            request.partName(),
            request.serialNumber(),
            request.inventoryNumber(),
            request.estimatedValue(),
            request.warehouseBuildingId(),
            request.warehouseRoomId(),
            request.notes(),
            actor);

    Instant now = Instant.now();
    BigDecimal estimatedValue =
        request.estimatedValue() != null
            ? request.estimatedValue().setScale(2, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);

    WriteOffReclaimedPart entity =
        WriteOffReclaimedPart.builder()
            .writtenOffItemId(parentItem.id())
            .reclaimedItemId(reclaimedItem.id())
            .partName(request.partName().trim())
            .serialNumber(request.serialNumber())
            .inventoryNumber(reclaimedItem.inventoryNumber())
            .estimatedValue(estimatedValue)
            .warehouseBuildingId(reclaimedItem.buildingId())
            .warehouseRoomId(reclaimedItem.roomId())
            .reclaimedAt(now)
            .reclaimedByUsername(actor)
            .notes(request.notes())
            .build();

    WriteOffReclaimedPart saved =
        writeOffReclaimedPartRepository != null
            ? writeOffReclaimedPartRepository.save(entity)
            : entity;

    String statusName =
        reclaimedItem.stateName() != null && !reclaimedItem.stateName().isBlank()
            ? reclaimedItem.stateName()
            : "OMBORDA";
    return toReclaimedPartDto(saved, statusName);
  }

  @Transactional(readOnly = true)
  public List<ReclaimedPartDto> listReclaimedParts(UUID writtenOffItemId) {
    if (writeOffReclaimedPartRepository == null || writtenOffItemId == null) {
      return List.of();
    }
    return writeOffReclaimedPartRepository
        .findAllByWrittenOffItemIdOrderByReclaimedAtDesc(writtenOffItemId)
        .stream()
        .map(part -> toReclaimedPartDto(part, "OMBORDA"))
        .toList();
  }

  @Transactional
  public DataWipeRecordDto recordDataWipe(UUID itemId, RecordDataWipeRequest request) {
    return assetLogisticsApi.recordDataWipe(itemId, request);
  }

  @Transactional(readOnly = true)
  public Optional<DataWipeRecordDto> getLatestDataWipe(UUID itemId) {
    return assetLogisticsApi.getLatestDataWipe(itemId);
  }

  private ReclaimedPartDto toReclaimedPartDto(
      WriteOffReclaimedPart entity, String warehouseStatus) {
    return new ReclaimedPartDto(
        entity.getId(),
        entity.getWrittenOffItemId(),
        entity.getReclaimedItemId(),
        entity.getPartName(),
        entity.getSerialNumber(),
        entity.getInventoryNumber(),
        entity.getEstimatedValue(),
        entity.getWarehouseBuildingId(),
        entity.getWarehouseRoomId(),
        warehouseStatus,
        entity.getReclaimedAt(),
        entity.getReclaimedByUsername(),
        entity.getNotes());
  }
}
