package uz.ttpu.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import uz.ttpu.inventory.asset.api.CreateItemRequest;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.asset.api.PublicItemCardDto;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.asset.service.AssetScheduledAlertJob;
import uz.ttpu.inventory.asset.service.AssetService;
import uz.ttpu.inventory.audit.api.AuditRecordDto;
import uz.ttpu.inventory.audit.service.AuditService;
import uz.ttpu.inventory.common.domain.AuditActionType;
import uz.ttpu.inventory.file.api.StoredFileDto;
import uz.ttpu.inventory.file.api.UploadFileRequest;
import uz.ttpu.inventory.file.domain.FileCategory;
import uz.ttpu.inventory.file.service.FileStorageService;
import uz.ttpu.inventory.finance.api.DepreciationForecastDto;
import uz.ttpu.inventory.finance.service.DepreciationService;
import uz.ttpu.inventory.integration.api.EImzoSignatureRecordDto;
import uz.ttpu.inventory.integration.api.HemisSyncResultDto;
import uz.ttpu.inventory.integration.api.OneCConnector;
import uz.ttpu.inventory.integration.api.OneCDepreciationRecordDto;
import uz.ttpu.inventory.integration.api.OneCNewItemExportRequest;
import uz.ttpu.inventory.integration.api.OutboxMessageDto;
import uz.ttpu.inventory.integration.api.OutboxWorkerRunSummaryDto;
import uz.ttpu.inventory.integration.api.VerifySignedPdfRequest;
import uz.ttpu.inventory.integration.domain.OutboxStatus;
import uz.ttpu.inventory.integration.service.EImzoService;
import uz.ttpu.inventory.integration.service.HemisSyncService;
import uz.ttpu.inventory.integration.service.OutboxWorkerService;
import uz.ttpu.inventory.inventory.api.CreateInventorySessionRequest;
import uz.ttpu.inventory.inventory.api.InventoryExpectedItemDto;
import uz.ttpu.inventory.inventory.api.InventoryProgressDashboardDto;
import uz.ttpu.inventory.inventory.api.InventoryReconciliationReportDto;
import uz.ttpu.inventory.inventory.api.InventorySessionDto;
import uz.ttpu.inventory.inventory.api.OfflineScanActionRequest;
import uz.ttpu.inventory.inventory.api.OfflineSessionPackageDto;
import uz.ttpu.inventory.inventory.api.OfflineSyncBatchRequest;
import uz.ttpu.inventory.inventory.api.OfflineSyncBatchResponseDto;
import uz.ttpu.inventory.inventory.api.RoomExpectedItemsDto;
import uz.ttpu.inventory.inventory.api.ScanInventoryItemRequest;
import uz.ttpu.inventory.inventory.domain.InventoryLineStatus;
import uz.ttpu.inventory.inventory.domain.InventorySessionStatus;
import uz.ttpu.inventory.inventory.domain.ReconciliationCategory;
import uz.ttpu.inventory.inventory.service.InventoryService;
import uz.ttpu.inventory.movement.api.IssueDispatchRequest;
import uz.ttpu.inventory.movement.api.MovementConfirmRequest;
import uz.ttpu.inventory.movement.api.MovementRecordDto;
import uz.ttpu.inventory.movement.api.TransferInitiateRequest;
import uz.ttpu.inventory.movement.domain.MovementEventType;
import uz.ttpu.inventory.movement.domain.MovementType;
import uz.ttpu.inventory.movement.service.MovementService;
import uz.ttpu.inventory.notification.api.NotificationLogDto;
import uz.ttpu.inventory.notification.domain.NotificationChannel;
import uz.ttpu.inventory.notification.service.NotificationService;
import uz.ttpu.inventory.receipt.api.CreateReceiptRequest;
import uz.ttpu.inventory.receipt.api.ReceiptDto;
import uz.ttpu.inventory.receipt.api.ReceiptLineRequest;
import uz.ttpu.inventory.receipt.domain.ReceiptStatus;
import uz.ttpu.inventory.receipt.service.ReceiptService;
import uz.ttpu.inventory.references.api.BuildingDto;
import uz.ttpu.inventory.references.api.BuildingRequest;
import uz.ttpu.inventory.references.api.CategoryDto;
import uz.ttpu.inventory.references.api.CategoryRequest;
import uz.ttpu.inventory.references.api.DepartmentDto;
import uz.ttpu.inventory.references.api.DepartmentRequest;
import uz.ttpu.inventory.references.api.EmployeeDto;
import uz.ttpu.inventory.references.api.EmployeeRequest;
import uz.ttpu.inventory.references.api.FloorDto;
import uz.ttpu.inventory.references.api.FloorRequest;
import uz.ttpu.inventory.references.api.ReferenceRevisionDto;
import uz.ttpu.inventory.references.api.RoomDto;
import uz.ttpu.inventory.references.api.RoomRequest;
import uz.ttpu.inventory.references.service.ReferenceService;
import uz.ttpu.inventory.repair.api.CompleteRepairRequest;
import uz.ttpu.inventory.repair.api.CreateRepairTicketRequest;
import uz.ttpu.inventory.repair.api.RepairTicketDto;
import uz.ttpu.inventory.repair.api.StartInProgressRepairRequest;
import uz.ttpu.inventory.repair.api.VendorRepairRequest;
import uz.ttpu.inventory.repair.domain.RepairTicketState;
import uz.ttpu.inventory.repair.service.RepairService;
import uz.ttpu.inventory.report.service.SxssfInventoryReportService;
import uz.ttpu.inventory.report.service.SxssfInventoryReportService.GeneratedXlsxReport;
import uz.ttpu.inventory.request.api.AssetRequestDto;
import uz.ttpu.inventory.request.api.CreateAssetRequestDto;
import uz.ttpu.inventory.request.domain.AssetRequestStatus;
import uz.ttpu.inventory.request.service.AssetRequestService;
import uz.ttpu.inventory.workflow.api.CreateWorkflowDefinitionRequest;
import uz.ttpu.inventory.workflow.api.StartWorkflowInstanceRequest;
import uz.ttpu.inventory.workflow.api.StepApprovalRequest;
import uz.ttpu.inventory.workflow.api.WorkflowDefinitionDto;
import uz.ttpu.inventory.workflow.api.WorkflowInstanceDto;
import uz.ttpu.inventory.workflow.api.WorkflowStepDefinitionRequest;
import uz.ttpu.inventory.workflow.domain.VerificationMethod;
import uz.ttpu.inventory.workflow.domain.WorkflowDocumentType;
import uz.ttpu.inventory.workflow.domain.WorkflowState;
import uz.ttpu.inventory.workflow.service.WorkflowService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private static PostgreSQLContainer<?> fallbackContainer;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        if (isLocalPostgresAvailable()) {
            registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/ttpu_inventory_test");
            registry.add("spring.datasource.username", () -> "ttpu");
            registry.add("spring.datasource.password", () -> "ttpu_secret");
        } else {
            fallbackContainer = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
            fallbackContainer.start();
            registry.add("spring.datasource.url", fallbackContainer::getJdbcUrl);
            registry.add("spring.datasource.username", fallbackContainer::getUsername);
            registry.add("spring.datasource.password", fallbackContainer::getPassword);
        }
    }

    private static boolean isLocalPostgresAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 5432), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Autowired
    private ReferenceService referenceService;

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private MovementService movementService;

    @Autowired
    private uz.ttpu.inventory.movement.service.MovementHandoverActService movementHandoverActService;

    @Autowired
    private RepairService repairService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AssetScheduledAlertJob assetScheduledAlertJob;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private uz.ttpu.inventory.inventory.service.InventoryDocumentPrintService inventoryDocumentPrintService;

    @Autowired
    private OutboxWorkerService outboxWorkerService;

    @Autowired
    private OneCConnector oneCConnector;

    @Autowired
    private HemisSyncService hemisSyncService;

    @Autowired
    private EImzoService eImzoService;

    @Autowired
    private DepreciationService depreciationService;

    @Autowired
    private SxssfInventoryReportService sxssfInventoryReportService;

    @Autowired
    private AssetRequestService assetRequestService;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void fullEndToEndPostgreSqlIntegrationAndTriggerVerification() throws Exception {
        // 1. Reference Data CRUD + JSONB + Soft Delete + Hibernate Envers Revisions
        String suffix = String.valueOf(System.currentTimeMillis());
        BuildingDto building = referenceService.createBuilding(
                new BuildingRequest("BLD-" + suffix, "Main Campus Building", "Tashkent, Little Ring Road 7", "Main block")
        );
        BuildingDto updatedBuilding = referenceService.updateBuilding(
                building.id(),
                new BuildingRequest("BLD-" + suffix, "Main Campus Building (Updated)", "Tashkent, Little Ring Road 7", "Updated")
        );
        assertThat(updatedBuilding.name()).contains("Updated");

        List<ReferenceRevisionDto<BuildingDto>> buildingRevisions = referenceService.getBuildingRevisions(building.id());
        assertThat(buildingRevisions).hasSizeGreaterThanOrEqualTo(2);

        FloorDto floor = referenceService.createFloor(new FloorRequest(building.id(), 2, "2nd Floor"));
        DepartmentDto dept = referenceService.createDepartment(
                new DepartmentRequest("DEP-" + suffix, "Computer Engineering", null, building.id())
        );
        RoomDto room1 = referenceService.createRoom(new RoomRequest(floor.id(), dept.id(), "201-" + suffix, "Lab 201", 30));
        RoomDto room2 = referenceService.createRoom(new RoomRequest(floor.id(), dept.id(), "202-" + suffix, "Lab 202", 30));

        EmployeeDto emp1 = referenceService.createEmployee(new EmployeeRequest(
                "EMP1-" + suffix, "Sardor", "Karimov", null,
                "sardor." + suffix + "@ttpu.uz", "+998901112233", "Lab Manager",
                dept.id(), room1.id(), "HEMIS-1", "1C-1"
        ));
        EmployeeDto emp2 = referenceService.createEmployee(new EmployeeRequest(
                "EMP2-" + suffix, "Dilshod", "Rakhimov", null,
                "dilshod." + suffix + "@ttpu.uz", "+998902223344", "Senior Lecturer",
                dept.id(), room2.id(), "HEMIS-2", "1C-2"
        ));

        CategoryDto category = referenceService.createCategory(new CategoryRequest(
                "CAT-" + suffix,
                "Workstation Laptops",
                null,
                5,
                Map.of("cpu", "Apple M3 Max", "ramGb", 64)
        ));
        assertThat(category.customFields()).containsEntry("cpu", "Apple M3 Max");

        // 2. Receipt Creation & Approval -> Auto-creates Items with 10-char Base32 QR IDs in OMBORDA
        ReceiptDto receipt = receiptService.createReceipt(new CreateReceiptRequest(
                "REC-" + suffix,
                "CTR-" + suffix,
                Instant.now(),
                "INV-" + suffix,
                Instant.now(),
                "Apple Authorized Reseller UZ",
                "301234567",
                Instant.now(),
                building.id(),
                emp1.id(),
                "Delivered in sealed boxes",
                List.of(new ReceiptLineRequest(
                        "MacBook Pro 16 M3 Max",
                        category.id(),
                        "SN-MBP-" + suffix,
                        new BigDecimal("42000000.00"),
                        1
                ))
        ));
        assertThat(receipt.status()).isEqualTo(ReceiptStatus.DRAFT);

        ReceiptDto approvedReceipt = receiptService.approveReceipt(receipt.id());
        assertThat(approvedReceipt.status()).isEqualTo(ReceiptStatus.CONFIRMED);

        List<ItemDto> warehouseItems = assetService.listItems(ItemState.OMBORDA, null, building.id());
        ItemDto createdItem = warehouseItems.stream()
                .filter(i -> ("SN-MBP-" + suffix).equals(i.serialNumber()))
                .findFirst()
                .orElseThrow();
        assertThat(createdItem.qrCode()).hasSize(10).matches("^[A-Z2-7]{10}$");

        // 3. Public Item Card (/a/{id}) & Export -> Verified in audit_record via ItemCardAuditAspect
        PublicItemCardDto publicCard = assetService.getPublicItemCard(createdItem.qrCode());
        assertThat(publicCard.ownershipLabel()).isEqualTo("TTPU mulki");
        assertThat(publicCard.inventoryNumber()).isEqualTo(createdItem.inventoryNumber());

        assetService.exportItemCard(createdItem.id());

        List<AuditRecordDto> itemAudits = auditService.listAuditRecords(createdItem.id().toString(), null);
        assertThat(itemAudits).extracting(AuditRecordDto::actionType)
                .contains(AuditActionType.CHANGE, AuditActionType.EXPORT);

        List<AuditRecordDto> qrReadAudits = auditService.listAuditRecords(createdItem.qrCode(), null);
        assertThat(qrReadAudits).extracting(AuditRecordDto::actionType)
                .contains(AuditActionType.READ);

        // 4. Issue Flow (OMBORDA -> YOLDA -> FOYDALANISHDA via TOTP)
        MovementRecordDto issueDispatched = movementService.dispatchIssue(new IssueDispatchRequest(
                null,
                createdItem.qrCode(),
                "ISS-" + suffix,
                building.id(),
                floor.id(),
                room1.id(),
                dept.id(),
                emp1.id(),
                emp1.id(),
                "Issuing to Lab 201"
        ));
        assertThat(issueDispatched.eventType()).isEqualTo(MovementEventType.ISSUE_DISPATCHED);
        assertThat(assetService.getItem(createdItem.id()).state()).isEqualTo(ItemState.YOLDA);

        MovementRecordDto issueConfirmed = movementService.confirmIssue(
                issueDispatched.operationId(),
                new MovementConfirmRequest(emp1.id(), "123456", "Accepted via TOTP")
        );
        assertThat(issueConfirmed.eventType()).isEqualTo(MovementEventType.ISSUE_ACCEPTED);
        assertThat(assetService.getItem(createdItem.id()).state()).isEqualTo(ItemState.FOYDALANISHDA);

        // 5. Transfer Flow (FOYDALANISHDA -> YOLDA -> FOYDALANISHDA)
        MovementRecordDto transferInitiated = movementService.initiateTransfer(new TransferInitiateRequest(
                createdItem.id(),
                null,
                "TRF-" + suffix,
                MovementType.TRANSFER_ROOM_TO_ROOM,
                building.id(),
                floor.id(),
                room2.id(),
                dept.id(),
                emp2.id(),
                emp1.id(),
                "Transferring to Lab 202"
        ));
        assertThat(assetService.getItem(createdItem.id()).state()).isEqualTo(ItemState.YOLDA);

        MovementRecordDto transferConfirmed = movementService.confirmTransfer(
                transferInitiated.operationId(),
                new MovementConfirmRequest(emp2.id(), "654321", "Accepted in Lab 202")
        );
        assertThat(transferConfirmed.eventType()).isEqualTo(MovementEventType.TRANSFER_ACCEPTED);
        ItemDto itemAfterTransfer = assetService.getItem(createdItem.id());
        assertThat(itemAfterTransfer.state()).isEqualTo(ItemState.FOYDALANISHDA);
        assertThat(itemAfterTransfer.roomId()).isEqualTo(room2.id());
        assertThat(itemAfterTransfer.custodianEmployeeId()).isEqualTo(emp2.id());

        // 6. Verify PostgreSQL Trigger rejects UPDATE and DELETE on movements table!
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE movements SET reason = 'tampered' WHERE id = ?",
                transferConfirmed.id()
        )).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("insert-only");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM movements WHERE id = ?",
                transferConfirmed.id()
        )).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("insert-only");

        // 7. Generic Workflow Approval Engine against PostgreSQL
        WorkflowDefinitionDto wfDef = workflowService.createDefinition(new CreateWorkflowDefinitionRequest(
                "WF-TRANSFER-" + suffix,
                "Transfer Approval Workflow",
                WorkflowDocumentType.TRANSFER,
                "Two-step approval",
                List.of(
                        new WorkflowStepDefinitionRequest(1, "Dept Head", "ROLE_DEPARTMENT_HEAD", VerificationMethod.TOTP, null, null, Map.of()),
                        new WorkflowStepDefinitionRequest(2, "Accountant", "ROLE_ACCOUNTANT", VerificationMethod.E_IMZO, null, null, Map.of())
                )
        ));
        WorkflowInstanceDto wfInst = workflowService.startInstance(new StartWorkflowInstanceRequest(
                wfDef.code(),
                transferConfirmed.operationId(),
                "TRF-" + suffix,
                new BigDecimal("42000000.00"),
                Map.of(),
                emp1.id(),
                true
        ));
        assertThat(wfInst.state()).isEqualTo(WorkflowState.IN_AGREEMENT);

        workflowService.approveStep(
                wfInst.id(),
                new StepApprovalRequest(VerificationMethod.TOTP, "112233", "Dept Head OK", emp1.id(), "sardor")
        );
        WorkflowInstanceDto wfApproved = workflowService.approveStep(
                wfInst.id(),
                new StepApprovalRequest(VerificationMethod.E_IMZO, "PKCS7-SIGNED-PAYLOAD", "Accountant OK", emp2.id(), "dilshod")
        );
        assertThat(wfApproved.state()).isEqualTo(WorkflowState.APPROVED);

        // 8. Repair & Notification Service + ShedLock Scheduled Alerts against PostgreSQL
        RepairTicketDto repairTicket = repairService.createTicket(new CreateRepairTicketRequest(
                "RPR-" + suffix,
                createdItem.id(),
                emp2.id(),
                emp1.id(),
                "Display backlight flickering",
                Instant.now().plus(7, ChronoUnit.DAYS)
        ));
        assertThat(repairTicket.state()).isEqualTo(RepairTicketState.NEW);

        RepairTicketDto inProgressTicket = repairService.startInProgress(
                repairTicket.id(),
                new StartInProgressRepairRequest(emp1.id(), Instant.now().plus(7, ChronoUnit.DAYS))
        );
        assertThat(inProgressTicket.state()).isEqualTo(RepairTicketState.IN_PROGRESS);

        RepairTicketDto inServiceTicket = repairService.sendToVendorRepair(
                repairTicket.id(),
                new VendorRepairRequest(
                        "Apple Service Center Tashkent",
                        "ASC-" + suffix,
                        new BigDecimal("4500000.00"),
                        Instant.now().plus(14, ChronoUnit.DAYS)
                )
        );
        assertThat(inServiceTicket.state()).isEqualTo(RepairTicketState.IN_SERVICE);
        assertThat(assetService.getItem(createdItem.id()).state()).isEqualTo(ItemState.TAMIRDA);

        List<NotificationLogDto> repairNotifications = notificationService.listNotificationsByReference(repairTicket.id());
        assertThat(repairNotifications).hasSize(3);
        assertThat(repairNotifications).extracting(NotificationLogDto::channel)
                .containsExactlyInAnyOrder(NotificationChannel.EMAIL, NotificationChannel.TELEGRAM, NotificationChannel.PUSH);

        RepairTicketDto doneTicket = repairService.completeRepair(
                repairTicket.id(),
                new CompleteRepairRequest(new BigDecimal("4200000.00"), "Display assembly replaced under service contract")
        );
        assertThat(doneTicket.state()).isEqualTo(RepairTicketState.DONE);
        assertThat(assetService.getItem(createdItem.id()).state()).isEqualTo(ItemState.FOYDALANISHDA);

        RepairTicketDto closedTicket = repairService.closeTicket(repairTicket.id());
        assertThat(closedTicket.state()).isEqualTo(RepairTicketState.CLOSED);

        // Trigger ShedLock scheduled alert jobs and verify locks are persisted in shedlock table
        assetScheduledAlertJob.runDailyOverdueReturnsJob();
        assetScheduledAlertJob.runDailyExpiringWarrantiesJob();
        repairService.runDailyOverdueRepairTicketsJob();

        Integer shedlockCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shedlock", Integer.class);
        assertThat(shedlockCount).isGreaterThanOrEqualTo(3);

        // 9. Inventory Sessions & STOMP WebSocket Live Progress Dashboard against PostgreSQL
        InventorySessionDto invSession = inventoryService.createSession(new CreateInventorySessionRequest(
                "INV-SES-" + suffix,
                "ORD-" + suffix,
                Instant.now(),
                Instant.now(),
                emp1.id(),
                List.of(emp1.id(), emp2.id()),
                List.of(room1.id(), room2.id()),
                "Milestone 4 Inventory Session",
                true
        ));
        assertThat(invSession.status()).isEqualTo(InventorySessionStatus.IN_PROGRESS);

        List<RoomExpectedItemsDto> expectedLists = inventoryService.getExpectedListsByRoom(invSession.id());
        assertThat(expectedLists).hasSize(2);

        RoomExpectedItemsDto room2Expected = inventoryService.getRoomExpectedList(invSession.id(), room2.id());
        assertThat(room2Expected.expectedCount()).isEqualTo(1);
        assertThat(room2Expected.notFoundCount()).isEqualTo(1);
        assertThat(room2Expected.expectedItems().get(0).itemId()).isEqualTo(createdItem.id());

        // Connect live STOMP WebSocket client to ws://localhost:{port}/ws/inventory
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);

        BlockingQueue<InventoryProgressDashboardDto> receivedDashboards = new LinkedBlockingQueue<>();
        StompSession stompSession = stompClient
                .connectAsync("ws://localhost:" + port + "/ws/inventory", new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        String topic = InventoryService.TOPIC_SESSION_PROGRESS_PREFIX
                + invSession.id()
                + InventoryService.TOPIC_SESSION_PROGRESS_SUFFIX;
        stompSession.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return InventoryProgressDashboardDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedDashboards.offer((InventoryProgressDashboardDto) payload);
            }
        });

        InventoryExpectedItemDto scannedItem = inventoryService.scanItem(
                invSession.id(),
                new ScanInventoryItemRequest(null, createdItem.qrCode(), room2.id(), emp2.id(), "Scanned via QR")
        );
        assertThat(scannedItem.lineStatus()).isEqualTo(InventoryLineStatus.FOUND);

        InventoryProgressDashboardDto liveStompMessage = receivedDashboards.poll(5, TimeUnit.SECONDS);
        assertThat(liveStompMessage).isNotNull();
        assertThat(liveStompMessage.sessionId()).isEqualTo(invSession.id());
        assertThat(liveStompMessage.status()).isEqualTo(InventorySessionStatus.COMPLETED);
        assertThat(liveStompMessage.totalRooms()).isEqualTo(2);
        assertThat(liveStompMessage.completedRooms()).isEqualTo(2);
        assertThat(liveStompMessage.totalExpectedItems()).isEqualTo(1);
        assertThat(liveStompMessage.totalFoundItems()).isEqualTo(1);
        assertThat(liveStompMessage.totalNotFoundItems()).isEqualTo(0);
        assertThat(liveStompMessage.overallCompletionPercentage()).isEqualByComparingTo("100.00");

        stompSession.disconnect();
        stompClient.stop();

        // 10. Offline Sync Package, Idempotent Batch Sync & Reconciliation (-> QIDIRUVDA) against PostgreSQL
        ItemDto itemMisplaced = assetService.createItem(new CreateItemRequest(
                "INV-MIS-" + suffix, "SN-MIS-" + suffix, null, "Misplaced Oscilloscope",
                "Expected in Room 1, scanned in Room 2", category.id(),
                new BigDecimal("8000000.00"), new BigDecimal("7500000.00"),
                Instant.now(), null, null,
                building.id(), floor.id(), room1.id(), dept.id(), emp1.id(),
                Map.of(), "1C-MIS-" + suffix
        ));
        ItemDto itemMissing = assetService.createItem(new CreateItemRequest(
                "INV-MISS-" + suffix, "SN-MISS-" + suffix, null, "Missing Projector",
                "Expected in Room 1, not scanned -> QIDIRUVDA", category.id(),
                new BigDecimal("9000000.00"), new BigDecimal("8500000.00"),
                Instant.now(), null, null,
                building.id(), floor.id(), room1.id(), dept.id(), emp1.id(),
                Map.of(), "1C-MISS-" + suffix
        ));

        InventorySessionDto syncSession = inventoryService.createSession(new CreateInventorySessionRequest(
                "SYNC-SES-" + suffix,
                "ORD-SYNC-" + suffix,
                Instant.now(),
                Instant.now(),
                emp1.id(),
                List.of(emp1.id(), emp2.id()),
                List.of(room1.id(), room2.id()),
                "Offline Sync & Reconciliation Session",
                true
        ));

        OfflineSessionPackageDto offlinePkg = inventoryService.getOfflineSessionPackage(syncSession.id());
        assertThat(offlinePkg.version()).isGreaterThanOrEqualTo(2L);
        assertThat(offlinePkg.expectedItems()).hasSize(3);

        UUID clientActionJoyida = UUID.randomUUID();
        UUID clientActionMisplaced = UUID.randomUUID();
        UUID clientActionSurplus = UUID.randomUUID();

        OfflineSyncBatchResponseDto batchResult = inventoryService.processOfflineBatch(new OfflineSyncBatchRequest(
                syncSession.id(),
                List.of(
                        // Joyida: createdItem expected in room2, scanned in room2
                        new OfflineScanActionRequest(clientActionJoyida, syncSession.id(), room2.id(), emp2.id(), createdItem.id(), createdItem.qrCode(), null, null, Instant.now(), "In place"),
                        // Duplicate of clientActionJoyida -> idempotently ignored
                        new OfflineScanActionRequest(clientActionJoyida, syncSession.id(), room2.id(), emp2.id(), createdItem.id(), createdItem.qrCode(), null, null, Instant.now(), "Duplicate retry"),
                        // Boshqa xonada topildi: itemMisplaced expected in room1, scanned in room2
                        new OfflineScanActionRequest(clientActionMisplaced, syncSession.id(), room2.id(), emp2.id(), itemMisplaced.id(), itemMisplaced.qrCode(), null, null, Instant.now(), "Found in Room 2"),
                        // Ortiqcha: unknown surplus item scanned in room1
                        new OfflineScanActionRequest(clientActionSurplus, syncSession.id(), room1.id(), emp1.id(), null, "SURPLUS999", "INV-SURPLUS-" + suffix, "Surplus Whiteboard", Instant.now(), "Unlisted surplus")
                )
        ));
        assertThat(batchResult.totalSubmitted()).isEqualTo(4);
        assertThat(batchResult.appliedCount()).isEqualTo(3);
        assertThat(batchResult.duplicateIgnoredCount()).isEqualTo(1);

        InventoryReconciliationReportDto reconciliationReport = inventoryService.reconcileSession(syncSession.id());
        assertThat(reconciliationReport.joyidaCount()).isEqualTo(1);
        assertThat(reconciliationReport.boshqaXonadaTopildiCount()).isEqualTo(1);
        assertThat(reconciliationReport.topilmadiCount()).isEqualTo(1);
        assertThat(reconciliationReport.ortiqchaCount()).isEqualTo(1);

        assertThat(reconciliationReport.joyida().get(0).category()).isEqualTo(ReconciliationCategory.JOYIDA);
        assertThat(reconciliationReport.boshqaXonadaTopildi().get(0).category()).isEqualTo(ReconciliationCategory.BOSHQA_XONADA_TOPILDI);
        assertThat(reconciliationReport.topilmadi().get(0).category()).isEqualTo(ReconciliationCategory.TOPILMADI);
        assertThat(reconciliationReport.ortiqcha().get(0).category()).isEqualTo(ReconciliationCategory.ORTIQCHA);

        // Verify Topilmadi item was moved to QIDIRUVDA in PostgreSQL!
        assertThat(assetService.getItem(itemMissing.id()).state()).isEqualTo(ItemState.QIDIRUVDA);

        // 11. Transactional Outbox Pattern & Scheduled Resilience4j Worker against PostgreSQL
        List<OutboxMessageDto> pendingOutbox = outboxWorkerService.listOutboxMessages(OutboxStatus.PENDING, null);
        assertThat(pendingOutbox).hasSizeGreaterThanOrEqualTo(2); // Enqueued by ReceiptApprovedEvent & WorkflowCompletedEvent

        OutboxWorkerRunSummaryDto outboxSummary = outboxWorkerService.processPendingOutboxMessages();
        assertThat(outboxSummary.processedCount()).isGreaterThanOrEqualTo(2);
        assertThat(outboxSummary.failedCount()).isZero();

        List<OutboxMessageDto> processedOutbox = outboxWorkerService.listOutboxMessages(OutboxStatus.PROCESSED, null);
        assertThat(processedOutbox).hasSizeGreaterThanOrEqualTo(2);

        // 12. External Connectors (1C, HEMIS, E-IMZO) against PostgreSQL
        OutboxMessageDto exportedTo1C = oneCConnector.sendNewItemToOneC(new OneCNewItemExportRequest(
                createdItem.id(),
                createdItem.inventoryNumber(),
                createdItem.name(),
                createdItem.serialNumber(),
                createdItem.categoryId(),
                createdItem.purchasePrice(),
                createdItem.commissionedAt(),
                createdItem.departmentId(),
                createdItem.custodianEmployeeId()
        ));
        assertThat(exportedTo1C.eventType()).isEqualTo("ONE_C_NEW_ITEM_EXPORT");

        List<OneCDepreciationRecordDto> depApplied = oneCConnector.receiveDepreciationData(List.of(
                new OneCDepreciationRecordDto(
                        createdItem.id(),
                        createdItem.inventoryNumber(),
                        "1C-ASSET-" + suffix,
                        new BigDecimal("2000000.00"),
                        new BigDecimal("40000000.00"),
                        Instant.now()
                )
        ));
        assertThat(depApplied).hasSize(1);
        assertThat(assetService.getItem(createdItem.id()).residualValue()).isEqualByComparingTo("40000000.00");

        HemisSyncResultDto hemisSync = hemisSyncService.syncDailyHemisUpdates();
        assertThat(hemisSync.departmentsSynced()).isGreaterThanOrEqualTo(2);
        assertThat(hemisSync.employeesSynced()).isGreaterThanOrEqualTo(2);

        EImzoSignatureRecordDto eimzoVerified = eImzoService.verifyAndRecordSignedPdf(new VerifySignedPdfRequest(
                "INVENTORY_ACT",
                syncSession.id(),
                syncSession.sessionCode(),
                "JVBERi0xLjcKMSAwIG9iaiA8PCAvVHlwZSAvQ2F0YWxvZyA+PiBlbmRvYmoKJSVFT0YK",
                "31504881230045",
                "201122334",
                "KARIMOV SARDOR RUSTAMOVICH",
                "TTPU",
                "CERT-" + suffix
        ));
        assertThat(eimzoVerified.verified()).isTrue();
        assertThat(eImzoService.listSignaturesForDocument(syncSession.id())).hasSize(1);

        // 13. Straight-Line Depreciation Forecast & SXSSF Streaming XLSX Report against PostgreSQL
        DepreciationForecastDto depForecast = depreciationService.calculateForecastForItem(
                createdItem.id(),
                new BigDecimal("2000000.00"),
                5,
                1
        );
        assertThat(depForecast.annualDepreciation()).isEqualByComparingTo("8000000.00");
        assertThat(depForecast.yearlySchedule()).hasSize(5);

        GeneratedXlsxReport dbXlsxReport = sxssfInventoryReportService.generateInventoryXlsxFromDatabase("admin@ttpu.uz");
        assertThat(dbXlsxReport.log().rowCount()).isGreaterThanOrEqualTo(3);
        assertThat(dbXlsxReport.log().generationTimeMs()).isLessThan(30_000L);
        assertThat(dbXlsxReport.xlsxBytes()).isNotEmpty();

        // 14. Asset Requisition Requests (`request` module) & Stored File Metadata (`file` module) against PostgreSQL
        AssetRequestDto createdReq = assetRequestService.createRequest(new CreateAssetRequestDto(
                "REQ-" + suffix,
                category.id(),
                "Dell UltraSharp 27 Workstation Monitor",
                2,
                new BigDecimal("6500000.00"),
                dept.id(),
                building.id(),
                emp1.id(),
                "Required for CAD/CAM laboratory workstations"
        ));
        assertThat(createdReq.status()).isEqualTo(AssetRequestStatus.DRAFT);
        assertThat(createdReq.totalEstimatedCost()).isEqualByComparingTo("13000000.00");

        AssetRequestDto submittedReq = assetRequestService.submitRequest(createdReq.id());
        assertThat(submittedReq.status()).isEqualTo(AssetRequestStatus.SUBMITTED);

        // Approve AssetRequest via Generic Workflow Engine (SERVICE_REQUEST) -> auto-updates AssetRequest to APPROVED
        WorkflowDefinitionDto reqWfDef = workflowService.createDefinition(new CreateWorkflowDefinitionRequest(
                "WF-REQ-" + suffix,
                "Service Request Approval Workflow",
                WorkflowDocumentType.SERVICE_REQUEST,
                "Single-step department head approval",
                List.of(
                        new WorkflowStepDefinitionRequest(1, "Dept Head", "ROLE_DEPARTMENT_HEAD", VerificationMethod.TOTP, null, null, Map.of())
                )
        ));
        WorkflowInstanceDto reqWfInst = workflowService.startInstance(new StartWorkflowInstanceRequest(
                reqWfDef.code(),
                createdReq.id(),
                createdReq.requestNumber(),
                createdReq.totalEstimatedCost(),
                Map.of(),
                emp1.id(),
                true
        ));
        workflowService.approveStep(
                reqWfInst.id(),
                new StepApprovalRequest(VerificationMethod.TOTP, "445566", "Approved via Workflow", emp1.id(), "dean@ttpu.uz")
        );

        AssetRequestDto approvedReq = assetRequestService.getById(createdReq.id());
        assertThat(approvedReq.status()).isEqualTo(AssetRequestStatus.APPROVED);

        AssetRequestDto fulfilledReq = assetRequestService.fulfillRequest(createdReq.id(), createdItem.id(), "warehouse@ttpu.uz");
        assertThat(fulfilledReq.status()).isEqualTo(AssetRequestStatus.FULFILLED);
        assertThat(fulfilledReq.fulfilledItemId()).isEqualTo(createdItem.id());

        // 15. Write-Off Workflow Completion -> Automatically transitions recommended item to HISOBDAN_CHIQARILGAN
        assetService.transitionItemState(
                itemMissing.id(),
                new uz.ttpu.inventory.asset.api.TransitionItemRequest(
                        uz.ttpu.inventory.asset.domain.ItemEvent.RECOMMEND_WRITE_OFF,
                        "WO-REC-" + suffix
                )
        );
        assertThat(assetService.getItem(itemMissing.id()).state())
                .isEqualTo(ItemState.HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN);

        WorkflowDefinitionDto woWfDef = workflowService.createDefinition(new CreateWorkflowDefinitionRequest(
                "WF-WRITEOFF-" + suffix,
                "Asset Write-Off Approval Workflow",
                WorkflowDocumentType.WRITE_OFF,
                "Commission & Rectorate write-off approval",
                List.of(
                        new WorkflowStepDefinitionRequest(1, "Accountant", "ROLE_ACCOUNTANT", VerificationMethod.E_IMZO, null, null, Map.of())
                )
        ));
        WorkflowInstanceDto woWfInst = workflowService.startInstance(new StartWorkflowInstanceRequest(
                woWfDef.code(),
                itemMissing.id(),
                "WO-ACT-" + suffix,
                itemMissing.residualValue(),
                Map.of(),
                emp1.id(),
                true
        ));
        workflowService.approveStep(
                woWfInst.id(),
                new StepApprovalRequest(VerificationMethod.E_IMZO, "PKCS7-WRITEOFF-SIG", "Approved write-off", emp1.id(), "accountant@ttpu.uz")
        );
        assertThat(assetService.getItem(itemMissing.id()).state())
                .isEqualTo(ItemState.HISOBDAN_CHIQARILGAN);

        StoredFileDto storedFile = fileStorageService.storeFile(
                new UploadFileRequest(
                        "inventory-act-" + suffix + ".pdf",
                        "application/pdf",
                        "JVBERi0xLjcKMSAwIG9iaiA8PCAvVHlwZSAvQ2F0YWxvZyA+PiBlbmRvYmoKJSVFT0YK",
                        FileCategory.SIGNED_PDF_ACT,
                        syncSession.id()
                ),
                "admin@ttpu.uz"
        );
        assertThat(storedFile.sha256Checksum()).hasSize(64);
        assertThat(fileStorageService.listByReferenceEntity(syncSession.id())).hasSize(1);

        // 16. Security Hardening Verification: HSTS, X-Frame-Options, Content-Security-Policy & Bucket4j Login Rate Limiting
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest publicCardReq = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/a/" + createdItem.qrCode()))
                .GET()
                .build();
        java.net.http.HttpResponse<String> publicCardResp = httpClient.send(
                publicCardReq,
                java.net.http.HttpResponse.BodyHandlers.ofString()
        );
        assertThat(publicCardResp.statusCode()).isEqualTo(200);
        assertThat(publicCardResp.headers().firstValue("Strict-Transport-Security"))
                .hasValueSatisfying(v -> assertThat(v).contains("max-age=31536000").contains("includeSubDomains"));
        assertThat(publicCardResp.headers().firstValue("X-Frame-Options"))
                .hasValue("DENY");
        assertThat(publicCardResp.headers().firstValue("Content-Security-Policy"))
                .hasValueSatisfying(v -> assertThat(v).contains("default-src 'self'").contains("frame-ancestors 'none'"));

        // Verify OpenAPI / Swagger UI endpoints (/v3/api-docs and /swagger-ui/index.html)
        java.net.http.HttpRequest openApiReq = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/v3/api-docs"))
                .GET()
                .build();
        java.net.http.HttpResponse<String> openApiResp = httpClient.send(
                openApiReq,
                java.net.http.HttpResponse.BodyHandlers.ofString()
        );
        assertThat(openApiResp.statusCode()).isEqualTo(200);
        assertThat(openApiResp.body())
                .contains("Raqamli Inventar")
                .contains("01. Security & Authentication")
                .contains("17. Streaming XLSX Inventory Reports (Apache POI SXSSF)");

        java.net.http.HttpRequest swaggerUiReq = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/swagger-ui/index.html"))
                .GET()
                .build();
        java.net.http.HttpResponse<String> swaggerUiResp = httpClient.send(
                swaggerUiReq,
                java.net.http.HttpResponse.BodyHandlers.ofString()
        );
        assertThat(swaggerUiResp.statusCode()).isEqualTo(200);
        assertThat(swaggerUiResp.body()).contains("swagger-ui");

        // Verify Bucket4j rate limiting on /api/v1/auth/login (default capacity = 10 requests/minute per IP)
        String rateLimitTestIp = "203.0.113." + (System.currentTimeMillis() % 200 + 1);
        String loginPayload = "{\"username\":\"nonexistent\",\"password\":\"wrong\",\"totpCode\":\"123456\"}";
        int lastStatus = 0;
        for (int attempt = 1; attempt <= 11; attempt++) {
            java.net.http.HttpRequest loginReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                    .header("Content-Type", "application/json")
                    .header("X-Forwarded-For", rateLimitTestIp)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(loginPayload))
                    .build();
            java.net.http.HttpResponse<String> loginResp = httpClient.send(
                    loginReq,
                    java.net.http.HttpResponse.BodyHandlers.ofString()
            );
            lastStatus = loginResp.statusCode();
            if (attempt <= 10) {
                assertThat(lastStatus).isEqualTo(401);
                assertThat(loginResp.headers().firstValue("X-Rate-Limit-Remaining")).isPresent();
            } else {
                assertThat(lastStatus).isEqualTo(429);
                assertThat(loginResp.headers().firstValue("Retry-After")).isPresent();
            }
        }

        // 17. Milestone 6.2 & Docker Verification: k6 Script, Dockerfile, docker-compose.yml & 200-User Load Simulation
        java.nio.file.Path dockerfilePath = java.nio.file.Path.of("Dockerfile");
        assertThat(dockerfilePath).exists();
        assertThat(java.nio.file.Files.readString(dockerfilePath))
                .contains("FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder")
                .contains("FROM eclipse-temurin:21-jre-alpine AS runtime")
                .contains("mvn -B clean package -DskipTests")
                .contains("/app/app.jar")
                .contains("/actuator/health");

        java.nio.file.Path dockerComposePath = java.nio.file.Path.of("docker-compose.yml");
        assertThat(dockerComposePath).exists();
        assertThat(java.nio.file.Files.readString(dockerComposePath))
                .contains("postgres:16-alpine")
                .contains("redis:7-alpine")
                .contains("minio/minio:latest")
                .contains("ttpu-inventory-backend")
                .contains("condition: service_healthy")
                .contains("jdbc:postgresql://postgres:5432/")
                .contains("ttpu-inventory-net");

        assertThat(java.nio.file.Path.of(".dockerignore")).exists();
        assertThat(java.nio.file.Path.of(".env.example")).exists();

        java.nio.file.Path k6ScriptPath = java.nio.file.Path.of("load-test.js");
        assertThat(k6ScriptPath).exists();
        String k6ScriptContent = java.nio.file.Files.readString(k6ScriptPath);
        assertThat(k6ScriptContent)
                .contains("qr_public_scan")
                .contains("vus: 30")
                .contains("target: 170")
                .contains("/a/")
                .contains("p(99)<2000");

        List<String> validQrCodes = List.of(
                createdItem.qrCode(),
                itemMisplaced.qrCode(),
                itemMissing.qrCode()
        );
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<Long>> futures = new java.util.ArrayList<>(200);
            for (int vu = 0; vu < 200; vu++) {
                final int vuIndex = vu;
                final String targetQr = validQrCodes.get(vuIndex % validQrCodes.size());
                final String endpointPath = (vuIndex < 30) ? "/a/" + targetQr : "/api/v1/a/" + targetQr;
                futures.add(executor.submit(() -> {
                    long startNs = System.nanoTime();
                    java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create("http://localhost:" + port + endpointPath))
                            .header("Accept", "application/json")
                            .GET()
                            .build();
                    java.net.http.HttpResponse<String> resp = httpClient.send(
                            req,
                            java.net.http.HttpResponse.BodyHandlers.ofString()
                    );
                    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
                    assertThat(resp.statusCode()).isEqualTo(200);
                    assertThat(resp.body()).contains("TTPU mulki");
                    return elapsedMs;
                }));
            }
            for (java.util.concurrent.Future<Long> future : futures) {
                long durationMs = future.get(5, TimeUnit.SECONDS);
                assertThat(durationMs)
                        .as("Concurrent QR scan response time must be strictly under 2000ms")
                        .isLessThan(2000L);
            }
        }

        // 18. Prompt 7: OpenAPI Contract-First (openapi.yaml) & RFC 7807 (problem+json) with uz/ru/en Localization
        java.net.http.HttpRequest openApiYamlReq = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/openapi.yaml"))
                .GET()
                .build();
        java.net.http.HttpResponse<String> openApiYamlResp = httpClient.send(
                openApiYamlReq,
                java.net.http.HttpResponse.BodyHandlers.ofString()
        );
        assertThat(openApiYamlResp.statusCode()).isEqualTo(200);
        String openApiYaml = openApiYamlResp.body();
        assertThat(openApiYaml)
                .contains("openapi: 3.")
                .contains("/api/v1/references/buildings:")
                .contains("/api/v1/references/floors:")
                .contains("/api/v1/references/departments:")
                .contains("/api/v1/references/rooms:")
                .contains("/api/v1/references/employees:")
                .contains("/api/v1/references/categories:")
                .contains("/api/v1/assets:")
                .contains("/api/v1/assets/{id}/transitions:")
                .contains("/a/{id}:")
                .contains("/api/v1/receipts:")
                .contains("/api/v1/receipts/{id}/approve:")
                .contains("application/problem+json")
                .contains("ERR_RESOURCE_NOT_FOUND")
                .contains("http://localhost:4010");

        // Persist the generated openapi.yaml at the repository root for Prism Mock Server & Next.js / KMP client codegen
        java.nio.file.Files.writeString(java.nio.file.Path.of("openapi.yaml"), openApiYaml);
        assertThat(java.nio.file.Path.of("openapi.yaml")).exists();
        assertThat(java.nio.file.Path.of("scripts/prism-mock.sh")).exists();

        // Verify RFC 7807 (application/problem+json) with stable errorCode and uz, ru, en localized messages
        for (String lang : List.of("uz", "ru", "en")) {
            java.net.http.HttpRequest notFoundReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:" + port + "/a/NONEXISTENT"))
                    .header("Accept", "application/problem+json")
                    .header("Accept-Language", lang)
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> notFoundResp = httpClient.send(
                    notFoundReq,
                    java.net.http.HttpResponse.BodyHandlers.ofString()
            );
            assertThat(notFoundResp.statusCode()).isEqualTo(404);
            assertThat(notFoundResp.headers().firstValue("Content-Type"))
                    .isPresent()
                    .get()
                    .asString()
                    .contains("application/problem+json");
            assertThat(notFoundResp.body())
                    .contains("\"errorCode\":\"ERR_RESOURCE_NOT_FOUND\"")
                    .contains("\"uz\":\"So'ralgan ma'lumot topilmadi.\"")
                    .contains("\"ru\":\"Запрашиваемый ресурс не найден.\"")
                    .contains("\"en\":\"The requested resource was not found.\"");
        }

        // 19. Prompt 8: Official PDF Print Forms (FT-12.9 Handover Act, FT-9.13 Opis, Comparison Statement, Final Act) + E-IMZO Metadata
        var handoverPdf = movementHandoverActService.generateHandoverActPdf(transferConfirmed.operationId());
        assertThat(handoverPdf.formCode()).isEqualTo("FT-12.9");
        assertThat(new String(handoverPdf.pdfBytes(), 0, 5)).isEqualTo("%PDF-");

        var signedHandoverPdf = movementHandoverActService.signHandoverActPdf(
                transferConfirmed.operationId(),
                new uz.ttpu.inventory.movement.api.MovementSignPdfRequest(
                        "30101800010014",
                        "201122919",
                        "Prof. Jamshid Inoyatkhodjaev",
                        "TTPU",
                        "CERT-FT129-2026-01"
                )
        );
        assertThat(signedHandoverPdf.eimzoSignatureCount()).isGreaterThanOrEqualTo(1);
        assertThat(signedHandoverPdf.latestPkcs7SignatureId()).isNotBlank();

        var opisPdf = inventoryDocumentPrintService.generateOpisPdf(invSession.id());
        var comparisonPdf = inventoryDocumentPrintService.generateComparisonStatementPdf(invSession.id());
        var finalActPdf = inventoryDocumentPrintService.generateFinalActPdf(invSession.id());
        assertThat(opisPdf.formCode()).isEqualTo("FT-9.13");
        assertThat(comparisonPdf.formCode()).isEqualTo("FT-9.13");
        assertThat(finalActPdf.formCode()).isEqualTo("FT-9.13");
        assertThat(new String(opisPdf.pdfBytes(), 0, 5)).isEqualTo("%PDF-");
        assertThat(new String(comparisonPdf.pdfBytes(), 0, 5)).isEqualTo("%PDF-");
        assertThat(new String(finalActPdf.pdfBytes(), 0, 5)).isEqualTo("%PDF-");

        var signedFinalActPdf = inventoryDocumentPrintService.signSessionPdf(
                invSession.id(),
                "final-act",
                new uz.ttpu.inventory.inventory.api.InventorySignPdfRequest(
                        "30101800010014",
                        "201122919",
                        "Prof. Jamshid Inoyatkhodjaev",
                        "TTPU",
                        "CERT-FT913-2026-01"
                )
        );
        assertThat(signedFinalActPdf.eimzoSignatureCount()).isGreaterThanOrEqualTo(1);
        assertThat(signedFinalActPdf.latestPkcs7SignatureId()).isNotBlank();
    }
}
