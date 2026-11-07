package uz.ttpu.inventory.integration.api;

public interface OutboxIntegrationApi {

  OutboxMessageDto enqueueOutboxMessage(EnqueueOutboxRequest request);

  OutboxWorkerRunSummaryDto processPendingOutboxMessages();
}
