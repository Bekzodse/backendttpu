package uz.ttpu.inventory.integration.api;

import java.util.List;
import java.util.UUID;

public interface OneCConnector {

    OutboxMessageDto sendNewItemToOneC(OneCNewItemExportRequest request);

    List<OneCDepreciationRecordDto> receiveDepreciationData(List<OneCDepreciationRecordDto> records);

    List<OneCDepreciationRecordDto> pullDepreciationFromOneCStub(List<UUID> itemIds);
}
