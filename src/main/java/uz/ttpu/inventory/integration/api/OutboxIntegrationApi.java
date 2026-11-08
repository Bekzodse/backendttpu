package uz.ttpu.inventory.integration.api;

import java.util.Map;
import java.util.UUID;

public interface OutboxIntegrationApi {

  OutboxMessageDto enqueueOutboxMessage(EnqueueOutboxRequest request);

  OutboxWorkerRunSummaryDto processPendingOutboxMessages();

  OutboxMessageDto retryOutboxMessage(UUID id);

  Map<String, Object> getCircuitBreakerDetails();
}
