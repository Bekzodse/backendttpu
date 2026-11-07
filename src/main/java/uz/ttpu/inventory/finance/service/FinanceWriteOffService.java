package uz.ttpu.inventory.finance.service;

import java.time.Instant;
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
import uz.ttpu.inventory.finance.api.WriteOffCompletionRequest;
import uz.ttpu.inventory.finance.api.WriteOffResultDto;

@Service
@RequiredArgsConstructor
public class FinanceWriteOffService {

  private final AssetLogisticsApi assetLogisticsApi;

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

    return new WriteOffResultDto(
        completed.id(),
        completed.inventoryNumber(),
        request.documentNumber(),
        hasExistingWipe,
        Instant.now());
  }

  @Transactional
  public DataWipeRecordDto recordDataWipe(UUID itemId, RecordDataWipeRequest request) {
    return assetLogisticsApi.recordDataWipe(itemId, request);
  }

  @Transactional(readOnly = true)
  public Optional<DataWipeRecordDto> getLatestDataWipe(UUID itemId) {
    return assetLogisticsApi.getLatestDataWipe(itemId);
  }
}
