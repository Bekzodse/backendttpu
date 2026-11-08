package uz.ttpu.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.asset.api.ItemStateChangedEvent;
import uz.ttpu.inventory.asset.domain.ItemEvent;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.integration.api.EImzoSignatureRecordDto;
import uz.ttpu.inventory.integration.api.EnqueueOutboxRequest;
import uz.ttpu.inventory.integration.api.HemisSyncResultDto;
import uz.ttpu.inventory.integration.api.OneCDepreciationRecordDto;
import uz.ttpu.inventory.integration.api.OneCNewItemExportRequest;
import uz.ttpu.inventory.integration.api.OutboxMessageDto;
import uz.ttpu.inventory.integration.api.OutboxWorkerRunSummaryDto;
import uz.ttpu.inventory.integration.api.VerifySignedPdfRequest;
import uz.ttpu.inventory.integration.domain.EImzoSignatureRecord;
import uz.ttpu.inventory.integration.domain.ExternalSystemType;
import uz.ttpu.inventory.integration.domain.ExternalSystemUnavailableException;
import uz.ttpu.inventory.integration.domain.OutboxMessage;
import uz.ttpu.inventory.integration.domain.OutboxStatus;
import uz.ttpu.inventory.integration.repository.EImzoSignatureRecordRepository;
import uz.ttpu.inventory.integration.repository.OutboxMessageRepository;
import uz.ttpu.inventory.integration.service.EImzoExternalVerificationStub;
import uz.ttpu.inventory.integration.service.EImzoService;
import uz.ttpu.inventory.integration.service.ExternalDispatchResponse;
import uz.ttpu.inventory.integration.service.ExternalSystemGateway;
import uz.ttpu.inventory.integration.service.HemisExternalClientStub;
import uz.ttpu.inventory.integration.service.HemisSyncService;
import uz.ttpu.inventory.integration.service.IntegrationMapper;
import uz.ttpu.inventory.integration.service.IntegrationResilienceConfig;
import uz.ttpu.inventory.integration.service.OneCConnectorService;
import uz.ttpu.inventory.integration.service.OutboxWorkerService;
import uz.ttpu.inventory.references.api.ReferenceSyncApi;

class OutboxWorkerAndResilienceTest {

  private OutboxMessageRepository outboxRepository;
  private ExternalSystemGateway externalSystemGateway;
  private RetryRegistry retryRegistry;
  private CircuitBreakerRegistry circuitBreakerRegistry;
  private OutboxWorkerService outboxWorkerService;

  private final List<OutboxMessage> storedMessages = new ArrayList<>();

  @BeforeEach
  void setUp() {
    outboxRepository = mock(OutboxMessageRepository.class);
    externalSystemGateway = mock(ExternalSystemGateway.class);

    IntegrationResilienceConfig resilienceConfig = new IntegrationResilienceConfig();
    retryRegistry = resilienceConfig.integrationRetryRegistry(3, 5L);
    circuitBreakerRegistry = resilienceConfig.integrationCircuitBreakerRegistry(50.0f, 4, 6, 30L);

    storedMessages.clear();

    when(outboxRepository.findByIdempotencyKey(any(String.class)))
        .thenAnswer(
            inv -> {
              String key = inv.getArgument(0);
              return storedMessages.stream()
                  .filter(m -> m.getIdempotencyKey().equals(key))
                  .findFirst();
            });

    when(outboxRepository.findById(any(UUID.class)))
        .thenAnswer(
            inv -> {
              UUID id = inv.getArgument(0);
              return storedMessages.stream().filter(m -> m.getId().equals(id)).findFirst();
            });

    when(outboxRepository.saveAndFlush(any(OutboxMessage.class)))
        .thenAnswer(
            inv -> {
              OutboxMessage msg = inv.getArgument(0);
              if (msg.getId() == null) {
                msg.setId(UUID.randomUUID());
                storedMessages.add(msg);
              }
              return msg;
            });

    when(outboxRepository.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            any(), any()))
        .thenAnswer(
            inv ->
                storedMessages.stream()
                    .filter(
                        m ->
                            m.getStatus() == OutboxStatus.PENDING
                                || m.getStatus() == OutboxStatus.FAILED)
                    .toList());

    IntegrationMapper mapper = Mappers.getMapper(IntegrationMapper.class);
    outboxWorkerService =
        new OutboxWorkerService(
            outboxRepository, externalSystemGateway, retryRegistry, circuitBreakerRegistry, mapper);
  }

  @Test
  void outboxWorkerRetriesTransientFailureAndMarksMessageProcessedWhenExternalSystemRecovers() {
    UUID receiptId = UUID.randomUUID();

    // 1. Enqueue outbox message (and verify idempotency on duplicate key)
    OutboxMessageDto enqueued =
        outboxWorkerService.enqueueOutboxMessage(
            new EnqueueOutboxRequest(
                "1C-REC-" + receiptId,
                ExternalSystemType.ONE_C,
                "Receipt",
                receiptId,
                "RECEIPT_APPROVED",
                "https://1c.ttpu.uz/api/v1/receipts",
                "POST",
                Map.of("receiptNumber", "REC-001"),
                5));
    OutboxMessageDto duplicate =
        outboxWorkerService.enqueueOutboxMessage(
            new EnqueueOutboxRequest(
                "1C-REC-" + receiptId,
                ExternalSystemType.ONE_C,
                "Receipt",
                receiptId,
                "RECEIPT_APPROVED",
                "https://1c.ttpu.uz/api/v1/receipts",
                "POST",
                Map.of("receiptNumber", "REC-001"),
                5));
    assertThat(duplicate.id()).isEqualTo(enqueued.id());
    assertThat(storedMessages).hasSize(1);

    // 2. External system fails twice transiently, then succeeds on 3rd attempt via Resilience4j
    // Retry
    AtomicInteger callCounter = new AtomicInteger(0);
    when(externalSystemGateway.dispatch(any(), any(), any(), any()))
        .thenAnswer(
            inv -> {
              int attempt = callCounter.incrementAndGet();
              if (attempt < 3) {
                throw new ExternalSystemUnavailableException(
                    "1C gateway 503 Service Unavailable (attempt " + attempt + ")");
              }
              return new ExternalDispatchResponse(200, "{\"1cRef\":\"DOC-998\"}");
            });

    OutboxWorkerRunSummaryDto summary = outboxWorkerService.processPendingOutboxMessages();
    assertThat(summary.processedCount()).isEqualTo(1);
    assertThat(summary.failedCount()).isZero();
    assertThat(summary.deadLetterCount()).isZero();

    verify(externalSystemGateway, times(3)).dispatch(any(), any(), any(), any());
    OutboxMessage processed = storedMessages.get(0);
    assertThat(processed.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
    assertThat(processed.getProcessedAt()).isNotNull();
    assertThat(processed.getResponseStatusCode()).isEqualTo(200);
    assertThat(processed.getLastError()).isNull();
  }

  @Test
  void
      outboxWorkerTripsCircuitBreakerOnSustainedDowntimeAndGracefullyHandlesOpenStateAndDeadLetter() {
    // Enqueue 2 outbox messages with maxAttempts = 2
    outboxWorkerService.enqueueOutboxMessage(
        new EnqueueOutboxRequest(
            "HEMIS-EMP-1",
            ExternalSystemType.HEMIS,
            "Employee",
            UUID.randomUUID(),
            "EMPLOYEE_SYNC",
            "https://hemis.ttpu.uz/api/v1/employees",
            "POST",
            Map.of("hemisId", "H-1"),
            2));
    outboxWorkerService.enqueueOutboxMessage(
        new EnqueueOutboxRequest(
            "HEMIS-EMP-2",
            ExternalSystemType.HEMIS,
            "Employee",
            UUID.randomUUID(),
            "EMPLOYEE_SYNC",
            "https://hemis.ttpu.uz/api/v1/employees",
            "POST",
            Map.of("hemisId", "H-2"),
            2));

    // External HEMIS system is completely down
    when(externalSystemGateway.dispatch(any(), any(), any(), any()))
        .thenThrow(new ExternalSystemUnavailableException("Connection refused: hemis.ttpu.uz"));

    // First worker run:
    // Message 1 -> Retry attempts 3 times (3 failures recorded in CircuitBreaker) -> marked FAILED
    // (attempt 1/2)
    // Message 2 -> 1st attempt fails (4th failure in CircuitBreaker -> CircuitBreaker trips to
    // OPEN!) -> marked FAILED (attempt 1/2)
    OutboxWorkerRunSummaryDto firstRun = outboxWorkerService.processPendingOutboxMessages();
    assertThat(firstRun.processedCount()).isZero();
    assertThat(firstRun.failedCount()).isEqualTo(2);
    assertThat(outboxWorkerService.getCircuitBreakerState(ExternalSystemType.HEMIS))
        .isEqualTo("OPEN");

    // Second worker run while CircuitBreaker is OPEN:
    // Both messages hit maxAttempts = 2 and move to DEAD_LETTER with "CircuitBreaker OPEN" recorded
    // in lastError
    storedMessages.forEach(m -> m.setNextAttemptAt(Instant.now().minusSeconds(1)));
    OutboxWorkerRunSummaryDto secondRun = outboxWorkerService.processPendingOutboxMessages();
    assertThat(secondRun.deadLetterCount()).isEqualTo(2);
    assertThat(storedMessages)
        .extracting(OutboxMessage::getStatus)
        .containsOnly(OutboxStatus.DEAD_LETTER);
    assertThat(storedMessages.get(0).getLastError()).contains("CircuitBreaker OPEN");
  }

  @Test
  void externalConnectorsOneCHemisAndEImzoVerifyAndSyncThroughResilience4jAndOutbox() {
    AssetLogisticsApi assetLogisticsApi = mock(AssetLogisticsApi.class);
    ReferenceSyncApi referenceSyncApi = mock(ReferenceSyncApi.class);
    EImzoSignatureRecordRepository signatureRecordRepository =
        mock(EImzoSignatureRecordRepository.class);

    when(signatureRecordRepository.saveAndFlush(any(EImzoSignatureRecord.class)))
        .thenAnswer(
            inv -> {
              EImzoSignatureRecord rec = inv.getArgument(0);
              if (rec.getId() == null) {
                rec.setId(UUID.randomUUID());
              }
              return rec;
            });

    IntegrationMapper mapper = Mappers.getMapper(IntegrationMapper.class);
    OneCConnectorService oneCConnector =
        new OneCConnectorService(outboxWorkerService, assetLogisticsApi);
    HemisSyncService hemisSyncService =
        new HemisSyncService(
            new HemisExternalClientStub(), referenceSyncApi, retryRegistry, circuitBreakerRegistry);
    EImzoService eImzoService =
        new EImzoService(
            signatureRecordRepository,
            new EImzoExternalVerificationStub(),
            retryRegistry,
            circuitBreakerRegistry,
            mapper);

    // 1. 1C Connector: Send new item via Resilience4j outbox & receive depreciation data
    UUID itemId = UUID.randomUUID();
    OutboxMessageDto itemExportOutbox =
        oneCConnector.sendNewItemToOneC(
            new OneCNewItemExportRequest(
                itemId,
                "INV-2026-900",
                "CNC Milling Machine Haas",
                "SN-HAAS-900",
                UUID.randomUUID(),
                new BigDecimal("250000000.00"),
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID()));
    assertThat(itemExportOutbox.targetSystem()).isEqualTo(ExternalSystemType.ONE_C);
    assertThat(itemExportOutbox.eventType()).isEqualTo("ONE_C_NEW_ITEM_EXPORT");

    when(assetLogisticsApi.applyOneCDepreciation(any(), any(), any(), any()))
        .thenReturn(
            new ItemDto(
                itemId,
                "INV-2026-900",
                "SN-HAAS-900",
                "QRHAAS9001",
                "CNC Milling Machine Haas",
                "CNC",
                UUID.randomUUID(),
                ItemState.FOYDALANISHDA,
                new BigDecimal("250000000.00"),
                new BigDecimal("245000000.00"),
                Instant.now(),
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Map.of(),
                "1C-HAAS-900",
                Instant.now(),
                Instant.now()));

    List<OneCDepreciationRecordDto> appliedDepreciation =
        oneCConnector.receiveDepreciationData(
            List.of(
                new OneCDepreciationRecordDto(
                    itemId,
                    "INV-2026-900",
                    "1C-HAAS-900",
                    new BigDecimal("5000000.00"),
                    new BigDecimal("245000000.00"),
                    Instant.now())));
    assertThat(appliedDepreciation).hasSize(1);
    assertThat(appliedDepreciation.get(0).residualValue()).isEqualByComparingTo("245000000.00");

    when(externalSystemGateway.dispatch(any(), any(), any(), any()))
        .thenReturn(new ExternalDispatchResponse(200, "{\"status\":\"OK\"}"));
    OutboxWorkerRunSummaryDto outboxRun = outboxWorkerService.processPendingOutboxMessages();
    assertThat(outboxRun.processedCount()).isEqualTo(2);

    // 2. HEMIS Sync Service: Daily sync of departments & employees
    HemisSyncResultDto hemisResult = hemisSyncService.syncDailyHemisUpdates();
    assertThat(hemisResult.departmentsSynced()).isEqualTo(2);
    assertThat(hemisResult.employeesSynced()).isEqualTo(2);
    verify(referenceSyncApi, times(2)).upsertDepartmentFromHemis(any(), any());
    verify(referenceSyncApi, times(2))
        .upsertEmployeeFromHemis(any(), any(), any(), any(), any(), any(), any(), any(), any());

    // 3. E-IMZO Service: Verify signed PDF blob and record signer's details
    String sampleSignedPdf =
        "%PDF-1.7\n1 0 obj << /Type /Catalog /EIMZO_PKCS7_SIGNED true >> endobj\n%%EOF";
    String base64Pdf =
        Base64.getEncoder().encodeToString(sampleSignedPdf.getBytes(StandardCharsets.UTF_8));
    UUID docId = UUID.randomUUID();

    EImzoSignatureRecordDto sigRecord =
        eImzoService.verifyAndRecordSignedPdf(
            new VerifySignedPdfRequest(
                "WRITE_OFF_ACT",
                docId,
                "ACT-2026-55",
                base64Pdf,
                "31504881230045",
                "201122334",
                "KARIMOV SARDOR RUSTAMOVICH",
                "Turin Polytechnic University in Tashkent",
                "EIMZO-CERT-778899"));
    assertThat(sigRecord.verified()).isTrue();
    assertThat(sigRecord.signerPinfl()).isEqualTo("31504881230045");
    assertThat(sigRecord.signerFullName()).isEqualTo("KARIMOV SARDOR RUSTAMOVICH");
    assertThat(sigRecord.certificateSerialNumber()).isEqualTo("EIMZO-CERT-778899");
    assertThat(sigRecord.pdfSha256Hash()).hasSize(64);
  }

  @Test
  void itemStateChangedEvent_enqueuesOutboxMessageTo1C() {
    UUID itemId = UUID.randomUUID();
    ItemStateChangedEvent event =
        new ItemStateChangedEvent(
            itemId,
            "INV-2026-888",
            ItemState.OMBORDA,
            ItemState.FOYDALANISHDA,
            ItemEvent.PUT_INTO_USE,
            "HANDOVER_ACT",
            "DOC-12345",
            Instant.now());

    outboxWorkerService.onItemStateChanged(event);

    assertThat(storedMessages).hasSize(1);
    OutboxMessage msg = storedMessages.get(0);
    assertThat(msg.getTargetSystem()).isEqualTo(ExternalSystemType.ONE_C);
    assertThat(msg.getEventType()).isEqualTo("ITEM_STATE_CHANGED");
    assertThat(msg.getAggregateType()).isEqualTo("Item");
    assertThat(msg.getAggregateId()).isEqualTo(itemId);
    assertThat(msg.getPayload())
        .containsEntry("inventoryNumber", "INV-2026-888")
        .containsEntry("fromState", "OMBORDA")
        .containsEntry("toState", "FOYDALANISHDA");
  }

  @Test
  void retryOutboxMessage_resetsFailedMessageToPending() {
    OutboxMessage msg =
        OutboxMessage.builder()
            .idempotencyKey("KEY-DEAD")
            .targetSystem(ExternalSystemType.ONE_C)
            .aggregateType("Item")
            .aggregateId(UUID.randomUUID())
            .eventType("ITEM_EXPORT")
            .endpointUrl("https://1c.ttpu.uz")
            .httpMethod("POST")
            .status(OutboxStatus.DEAD_LETTER)
            .attemptCount(5)
            .maxAttempts(5)
            .lastError("Timeout connecting to 1C")
            .nextAttemptAt(Instant.now().minusSeconds(100))
            .build();
    msg.setId(UUID.randomUUID());
    storedMessages.add(msg);

    OutboxMessageDto retried = outboxWorkerService.retryOutboxMessage(msg.getId());

    assertThat(retried.status()).isEqualTo(OutboxStatus.PENDING);
    assertThat(msg.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(msg.getLastError()).isNull();
  }
}
