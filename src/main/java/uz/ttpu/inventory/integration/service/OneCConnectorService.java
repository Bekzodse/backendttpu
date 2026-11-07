package uz.ttpu.inventory.integration.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.integration.api.EnqueueOutboxRequest;
import uz.ttpu.inventory.integration.api.OneCConnector;
import uz.ttpu.inventory.integration.api.OneCDepreciationRecordDto;
import uz.ttpu.inventory.integration.api.OneCNewItemExportRequest;
import uz.ttpu.inventory.integration.api.OutboxIntegrationApi;
import uz.ttpu.inventory.integration.api.OutboxMessageDto;
import uz.ttpu.inventory.integration.domain.ExternalSystemType;

@Service
@RequiredArgsConstructor
public class OneCConnectorService implements OneCConnector {

  private static final String ONE_C_ITEMS_ENDPOINT = "https://1c.ttpu.uz/api/v1/assets/items";
  private static final String ONE_C_DEPRECIATION_ACK_ENDPOINT =
      "https://1c.ttpu.uz/api/v1/assets/depreciation/ack";

  private final OutboxIntegrationApi outboxIntegrationApi;
  private final AssetLogisticsApi assetLogisticsApi;

  @Override
  @Transactional
  public OutboxMessageDto sendNewItemToOneC(OneCNewItemExportRequest request) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("itemId", request.itemId().toString());
    payload.put("inventoryNumber", request.inventoryNumber());
    payload.put("name", request.name());
    payload.put("serialNumber", request.serialNumber());
    if (request.categoryId() != null) {
      payload.put("categoryId", request.categoryId().toString());
    }
    payload.put("purchasePrice", request.purchasePrice());
    if (request.commissionedAt() != null) {
      payload.put("commissionedAt", request.commissionedAt().toString());
    }
    if (request.departmentId() != null) {
      payload.put("departmentId", request.departmentId().toString());
    }
    if (request.custodianEmployeeId() != null) {
      payload.put("custodianEmployeeId", request.custodianEmployeeId().toString());
    }

    return outboxIntegrationApi.enqueueOutboxMessage(
        new EnqueueOutboxRequest(
            "1C-EXPORT-ITEM-" + request.itemId(),
            ExternalSystemType.ONE_C,
            "Item",
            request.itemId(),
            "ONE_C_NEW_ITEM_EXPORT",
            ONE_C_ITEMS_ENDPOINT,
            "POST",
            payload,
            5));
  }

  @Override
  @Transactional
  public List<OneCDepreciationRecordDto> receiveDepreciationData(
      List<OneCDepreciationRecordDto> records) {
    List<OneCDepreciationRecordDto> applied = new ArrayList<>();
    for (OneCDepreciationRecordDto record : records) {
      ItemDto updatedItem =
          assetLogisticsApi.applyOneCDepreciation(
              record.itemId(),
              record.inventoryNumber(),
              record.oneCRefId(),
              record.residualValue());

      Map<String, Object> ackPayload = new LinkedHashMap<>();
      ackPayload.put("itemId", updatedItem.id().toString());
      ackPayload.put("inventoryNumber", updatedItem.inventoryNumber());
      ackPayload.put("oneCRefId", record.oneCRefId());
      ackPayload.put("depreciatedAmount", record.depreciatedAmount());
      ackPayload.put("residualValue", updatedItem.residualValue());
      ackPayload.put("periodDate", record.periodDate().toString());

      outboxIntegrationApi.enqueueOutboxMessage(
          new EnqueueOutboxRequest(
              "1C-DEPRECIATION-ACK-" + updatedItem.id() + "-" + record.periodDate().toEpochMilli(),
              ExternalSystemType.ONE_C,
              "Item",
              updatedItem.id(),
              "ONE_C_DEPRECIATION_APPLIED",
              ONE_C_DEPRECIATION_ACK_ENDPOINT,
              "POST",
              ackPayload,
              5));

      applied.add(
          new OneCDepreciationRecordDto(
              updatedItem.id(),
              updatedItem.inventoryNumber(),
              record.oneCRefId(),
              record.depreciatedAmount(),
              updatedItem.residualValue(),
              record.periodDate()));
    }
    return applied;
  }

  @Override
  @Transactional
  public List<OneCDepreciationRecordDto> pullDepreciationFromOneCStub(List<UUID> itemIds) {
    List<OneCDepreciationRecordDto> stubRecords = new ArrayList<>();
    Instant now = Instant.now();
    for (UUID itemId : itemIds) {
      Optional<ItemDto> itemOpt = assetLogisticsApi.findItemByIdOrQrOptional(itemId, null);
      if (itemOpt.isPresent()) {
        ItemDto item = itemOpt.get();
        BigDecimal currentResidual =
            item.residualValue() != null ? item.residualValue() : item.purchasePrice();
        BigDecimal monthlyDepreciation =
            currentResidual.multiply(new BigDecimal("0.02")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal newResidual = currentResidual.subtract(monthlyDepreciation).max(BigDecimal.ZERO);
        String oneCRef =
            item.oneCRefId() != null && !item.oneCRefId().isBlank()
                ? item.oneCRefId()
                : "1C-REF-" + item.inventoryNumber();

        stubRecords.add(
            new OneCDepreciationRecordDto(
                item.id(), item.inventoryNumber(), oneCRef, monthlyDepreciation, newResidual, now));
      }
    }
    return receiveDepreciationData(stubRecords);
  }
}
