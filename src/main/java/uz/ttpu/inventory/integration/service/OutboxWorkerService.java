package uz.ttpu.inventory.integration.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.asset.api.ItemStateChangedEvent;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.integration.api.EnqueueOutboxRequest;
import uz.ttpu.inventory.integration.api.OutboxIntegrationApi;
import uz.ttpu.inventory.integration.api.OutboxMessageDto;
import uz.ttpu.inventory.integration.api.OutboxWorkerRunSummaryDto;
import uz.ttpu.inventory.integration.domain.ExternalSystemType;
import uz.ttpu.inventory.integration.domain.OutboxMessage;
import uz.ttpu.inventory.integration.domain.OutboxStatus;
import uz.ttpu.inventory.integration.repository.OutboxMessageRepository;
import uz.ttpu.inventory.receipt.api.ReceiptApprovedEvent;
import uz.ttpu.inventory.workflow.api.WorkflowCompletedEvent;

@Service
@RequiredArgsConstructor
public class OutboxWorkerService implements OutboxIntegrationApi {

  private final OutboxMessageRepository outboxRepository;
  private final ExternalSystemGateway externalSystemGateway;
  private final RetryRegistry retryRegistry;
  private final CircuitBreakerRegistry circuitBreakerRegistry;
  private final IntegrationMapper integrationMapper;

  @Transactional(readOnly = true)
  public List<OutboxMessageDto> listOutboxMessages(
      OutboxStatus status, ExternalSystemType targetSystem) {
    List<OutboxMessage> messages;
    if (status != null) {
      messages = outboxRepository.findAllByStatusOrderByCreatedAtAsc(status);
    } else if (targetSystem != null) {
      messages = outboxRepository.findAllByTargetSystemOrderByCreatedAtDesc(targetSystem);
    } else {
      messages = outboxRepository.findAll();
    }
    return messages.stream().map(integrationMapper::toDto).toList();
  }

  @Transactional(readOnly = true)
  public OutboxMessageDto getOutboxMessage(UUID id) {
    OutboxMessage msg =
        outboxRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("OutboxMessage", id));
    return integrationMapper.toDto(msg);
  }

  @Override
  @Transactional
  public OutboxMessageDto enqueueOutboxMessage(EnqueueOutboxRequest request) {
    Optional<OutboxMessage> existing =
        outboxRepository.findByIdempotencyKey(request.idempotencyKey());
    if (existing.isPresent()) {
      return integrationMapper.toDto(existing.get());
    }

    OutboxMessage message =
        OutboxMessage.builder()
            .idempotencyKey(request.idempotencyKey())
            .targetSystem(request.targetSystem())
            .aggregateType(request.aggregateType())
            .aggregateId(request.aggregateId())
            .eventType(request.eventType())
            .endpointUrl(request.endpointUrl())
            .httpMethod(
                request.httpMethod() != null && !request.httpMethod().isBlank()
                    ? request.httpMethod()
                    : "POST")
            .payload(request.payload() != null ? new HashMap<>(request.payload()) : new HashMap<>())
            .status(OutboxStatus.PENDING)
            .attemptCount(0)
            .maxAttempts(
                request.maxAttempts() != null && request.maxAttempts() > 0
                    ? request.maxAttempts()
                    : 5)
            .nextAttemptAt(Instant.now())
            .build();

    return integrationMapper.toDto(outboxRepository.saveAndFlush(message));
  }

  @EventListener
  @Transactional
  public void onReceiptApproved(ReceiptApprovedEvent event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("receiptId", event.receiptId().toString());
    payload.put("receiptNumber", event.receiptNumber());
    payload.put("warehouseBuildingId", event.warehouseBuildingId().toString());
    payload.put("approvedAt", event.approvedAt().toString());
    payload.put("lineCount", event.lines().size());

    enqueueOutboxMessage(
        new EnqueueOutboxRequest(
            "1C-RECEIPT-APPROVED-" + event.receiptId(),
            ExternalSystemType.ONE_C,
            "Receipt",
            event.receiptId(),
            "RECEIPT_APPROVED",
            "https://1c.ttpu.uz/api/v1/receipts",
            "POST",
            payload,
            5));
  }

  @EventListener
  @Transactional
  public void onWorkflowCompleted(WorkflowCompletedEvent event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("workflowInstanceId", event.instanceId().toString());
    payload.put("documentType", event.documentTypeName());
    payload.put("documentId", event.documentId().toString());
    payload.put("documentNumber", event.documentNumber());
    payload.put("finalState", event.finalStateName());
    payload.put("completedAt", event.completedAt().toString());

    enqueueOutboxMessage(
        new EnqueueOutboxRequest(
            "1C-WORKFLOW-COMPLETED-" + event.instanceId(),
            ExternalSystemType.ONE_C,
            "WorkflowInstance",
            event.instanceId(),
            "WORKFLOW_COMPLETED",
            "https://1c.ttpu.uz/api/v1/workflows/decisions",
            "POST",
            payload,
            5));
  }

  @EventListener
  @Transactional
  public void onItemStateChanged(ItemStateChangedEvent event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("itemId", event.itemId().toString());
    payload.put("inventoryNumber", event.inventoryNumber());
    payload.put("fromState", event.fromStateName());
    payload.put("toState", event.toStateName());
    payload.put("event", event.eventName());
    payload.put("documentType", event.documentType());
    payload.put("documentRef", event.documentRef());
    payload.put(
        "occurredAt",
        event.occurredAt() != null ? event.occurredAt().toString() : Instant.now().toString());

    String dedupKey =
        "1C-ITEM-STATE-"
            + event.itemId()
            + "-"
            + (event.toStateName() != null ? event.toStateName() : "STATE")
            + "-"
            + (event.occurredAt() != null
                ? event.occurredAt().toEpochMilli()
                : System.currentTimeMillis());

    enqueueOutboxMessage(
        new EnqueueOutboxRequest(
            dedupKey,
            ExternalSystemType.ONE_C,
            "Item",
            event.itemId(),
            "ITEM_STATE_CHANGED",
            "https://1c.ttpu.uz/api/v1/items/state",
            "POST",
            payload,
            5));
  }

  @Override
  @Transactional
  public OutboxMessageDto retryOutboxMessage(UUID id) {
    OutboxMessage message =
        outboxRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("OutboxMessage", id));

    message.setStatus(OutboxStatus.PENDING);
    message.setNextAttemptAt(Instant.now());
    message.setLastError(null);
    return integrationMapper.toDto(outboxRepository.saveAndFlush(message));
  }

  @Scheduled(fixedDelayString = "${app.integration.outbox.poll-interval-ms:15000}")
  @SchedulerLock(
      name = "processTransactionalOutboxJob",
      lockAtLeastFor = "PT1S",
      lockAtMostFor = "PT2M")
  @Transactional
  public void runScheduledOutboxWorker() {
    processPendingOutboxMessages();
  }

  @Override
  @Transactional
  public OutboxWorkerRunSummaryDto processPendingOutboxMessages() {
    Instant now = Instant.now();
    List<OutboxMessage> dueMessages =
        outboxRepository.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Set.of(OutboxStatus.PENDING, OutboxStatus.FAILED), now);

    int processedCount = 0;
    int failedCount = 0;
    int deadLetterCount = 0;

    for (OutboxMessage message : dueMessages) {
      message.setStatus(OutboxStatus.PROCESSING);
      String systemKey = message.getTargetSystem().name();
      CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(systemKey);
      Retry retry = retryRegistry.retry(systemKey);

      Supplier<ExternalDispatchResponse> rawCall =
          () ->
              externalSystemGateway.dispatch(
                  message.getTargetSystem(),
                  message.getEndpointUrl(),
                  message.getHttpMethod(),
                  message.getPayload());

      Supplier<ExternalDispatchResponse> resilientCall =
          Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, rawCall));

      try {
        ExternalDispatchResponse response = resilientCall.get();
        message.setAttemptCount(message.getAttemptCount() + 1);
        message.setStatus(OutboxStatus.PROCESSED);
        message.setProcessedAt(Instant.now());
        message.setResponseStatusCode(response.statusCode());
        message.setResponseBody(response.responseBody());
        message.setLastError(null);
        outboxRepository.saveAndFlush(message);
        processedCount++;
      } catch (CallNotPermittedException cbOpenEx) {
        int nextAttemptNumber = message.getAttemptCount() + 1;
        message.setAttemptCount(nextAttemptNumber);
        message.setLastError("CircuitBreaker OPEN for " + systemKey + ": " + cbOpenEx.getMessage());
        if (nextAttemptNumber >= message.getMaxAttempts()) {
          message.setStatus(OutboxStatus.DEAD_LETTER);
          deadLetterCount++;
        } else {
          message.setStatus(OutboxStatus.FAILED);
          message.setNextAttemptAt(Instant.now().plus(computeBackoff(nextAttemptNumber)));
          failedCount++;
        }
        outboxRepository.saveAndFlush(message);
      } catch (Exception ex) {
        int nextAttemptNumber = message.getAttemptCount() + 1;
        message.setAttemptCount(nextAttemptNumber);
        message.setLastError(ex.getMessage());
        if (nextAttemptNumber >= message.getMaxAttempts()) {
          message.setStatus(OutboxStatus.DEAD_LETTER);
          deadLetterCount++;
        } else {
          message.setStatus(OutboxStatus.FAILED);
          message.setNextAttemptAt(Instant.now().plus(computeBackoff(nextAttemptNumber)));
          failedCount++;
        }
        outboxRepository.saveAndFlush(message);
      }
    }

    Map<String, String> cbStates = new LinkedHashMap<>();
    for (ExternalSystemType type : ExternalSystemType.values()) {
      cbStates.put(
          type.name(), circuitBreakerRegistry.circuitBreaker(type.name()).getState().name());
    }

    return new OutboxWorkerRunSummaryDto(
        processedCount, failedCount, deadLetterCount, cbStates, Instant.now());
  }

  public String getCircuitBreakerState(ExternalSystemType targetSystem) {
    return circuitBreakerRegistry.circuitBreaker(targetSystem.name()).getState().name();
  }

  private Duration computeBackoff(int attemptNumber) {
    long seconds = Math.min(300L, (long) Math.pow(2, Math.max(1, attemptNumber)));
    return Duration.ofSeconds(seconds);
  }
}
