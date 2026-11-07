package uz.ttpu.inventory.integration.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.integration.domain.ExternalSystemType;
import uz.ttpu.inventory.integration.domain.OutboxStatus;
import uz.ttpu.inventory.integration.service.EImzoService;
import uz.ttpu.inventory.integration.service.HemisSyncService;
import uz.ttpu.inventory.integration.service.OutboxWorkerService;

@RestController
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
@Tag(
        name = "15. External Integrations: Transactional Outbox, 1C, HEMIS & E-IMZO",
        description = "Transactional Outbox queue with Resilience4j Retry + CircuitBreaker, 1C Accounting item export & depreciation ingestion, HEMIS daily employee/department sync, and E-IMZO signed PDF verification."
)
public class IntegrationController {

    private final OutboxWorkerService outboxWorkerService;
    private final OneCConnector oneCConnector;
    private final HemisSyncService hemisSyncService;
    private final EImzoService eImzoService;

    @GetMapping("/outbox")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "List transactional outbox messages",
            description = "Returns outbox messages optionally filtered by status (PENDING, PROCESSING, PROCESSED, FAILED, DEAD_LETTER) and targetSystem (ONE_C, HEMIS, E_IMZO)."
    )
    public List<OutboxMessageDto> listOutboxMessages(
            @RequestParam(required = false) OutboxStatus status,
            @RequestParam(required = false) ExternalSystemType targetSystem
    ) {
        return outboxWorkerService.listOutboxMessages(status, targetSystem);
    }

    @GetMapping("/outbox/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get transactional outbox message by ID", description = "Retrieves a single outbox message including retry count, backoff schedule, and last error message.")
    public OutboxMessageDto getOutboxMessage(@PathVariable UUID id) {
        return outboxWorkerService.getOutboxMessage(id);
    }

    @PostMapping("/outbox")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'WAREHOUSE_MANAGER')")
    @Operation(
            summary = "Enqueue a message into the transactional outbox",
            description = "Idempotently enqueues an outgoing integration payload using a unique idempotencyKey."
    )
    public OutboxMessageDto enqueueOutboxMessage(@Valid @RequestBody EnqueueOutboxRequest request) {
        return outboxWorkerService.enqueueOutboxMessage(request);
    }

    @PostMapping("/outbox/process")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(
            summary = "Trigger Resilience4j transactional outbox worker",
            description = "Polls due PENDING/FAILED outbox messages and dispatches them through ExternalSystemGateway protected by Resilience4j Retry and CircuitBreaker."
    )
    public OutboxWorkerRunSummaryDto triggerOutboxWorker() {
        return outboxWorkerService.processPendingOutboxMessages();
    }

    @PostMapping("/1c/items")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'WAREHOUSE_MANAGER')")
    @Operation(
            summary = "Export a new inventory item to 1C Accounting",
            description = "Enqueues a new asset card export payload into the Resilience4j transactional outbox targeting 1C Accounting."
    )
    public OutboxMessageDto sendNewItemToOneC(@Valid @RequestBody OneCNewItemExportRequest request) {
        return oneCConnector.sendNewItemToOneC(request);
    }

    @PostMapping("/1c/depreciation")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(
            summary = "Receive actual depreciation updates from 1C Accounting",
            description = "Applies incoming 1C depreciation records to update Item.residualValue (NUMERIC(18,2)) and enqueues a 1C acknowledgment outbox message."
    )
    public List<OneCDepreciationRecordDto> receiveOneCDepreciation(
            @Valid @RequestBody List<OneCDepreciationRecordDto> records
    ) {
        return oneCConnector.receiveDepreciationData(records);
    }

    @PostMapping("/hemis/sync")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECTOR', 'VICE_RECTOR', 'HR_MANAGER', 'RECTORATE')")
    @Operation(
            summary = "Trigger HEMIS department and employee synchronization",
            description = "Fetches department and employee updates from HEMIS (wrapped in Resilience4j Retry + CircuitBreaker) and upserts them via ReferenceSyncApi."
    )
    public HemisSyncResultDto triggerDailyHemisSync() {
        return hemisSyncService.syncDailyHemisUpdates();
    }

    @PostMapping("/eimzo/verify-pdf")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Verify a signed PDF blob via E-IMZO and record signer details",
            description = "Computes the SHA-256 hash of the Base64 PDF payload, verifies the PKCS#7 signature via E-IMZO (protected by Resilience4j), and persists signer PINFL, TIN, full name, organization, and certificate serial."
    )
    public EImzoSignatureRecordDto verifySignedPdf(@Valid @RequestBody VerifySignedPdfRequest request) {
        return eImzoService.verifyAndRecordSignedPdf(request);
    }

    @GetMapping("/eimzo/documents/{documentId}/signatures")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "List verified E-IMZO digital signatures for a document",
            description = "Returns all recorded E-IMZO PKCS#7 signature verification records for the specified documentId."
    )
    public List<EImzoSignatureRecordDto> listDocumentSignatures(@PathVariable UUID documentId) {
        return eImzoService.listSignaturesForDocument(documentId);
    }
}
