package uz.ttpu.inventory.movement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import uz.ttpu.inventory.asset.domain.Item;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.asset.domain.ItemStateHistory;
import uz.ttpu.inventory.asset.repository.ItemRepository;
import uz.ttpu.inventory.asset.repository.ItemStateHistoryRepository;
import uz.ttpu.inventory.asset.service.AssetService;
import uz.ttpu.inventory.asset.service.ItemMapper;
import uz.ttpu.inventory.asset.service.ItemStateService;
import uz.ttpu.inventory.asset.service.QrIdService;
import uz.ttpu.inventory.movement.api.IssueDispatchRequest;
import uz.ttpu.inventory.movement.api.MovementConfirmRequest;
import uz.ttpu.inventory.movement.api.MovementRecordDto;
import uz.ttpu.inventory.movement.api.MovementRejectRequest;
import uz.ttpu.inventory.movement.api.TransferInitiateRequest;
import uz.ttpu.inventory.movement.domain.MovementEventType;
import uz.ttpu.inventory.movement.domain.MovementRecord;
import uz.ttpu.inventory.movement.domain.MovementType;
import uz.ttpu.inventory.movement.repository.MovementRepository;
import uz.ttpu.inventory.movement.service.MovementMapper;
import uz.ttpu.inventory.movement.service.MovementService;
import uz.ttpu.inventory.security.api.TotpVerificationApi;

class MovementServiceTest {

    private final Map<UUID, Item> itemStore = new HashMap<>();
    private final List<MovementRecord> movementLedger = new ArrayList<>();

    private MovementRepository movementRepository;
    private AssetService assetService;
    private TotpVerificationApi totpVerificationApi;
    private MovementService movementService;

    @BeforeEach
    void setUp() {
        ItemRepository itemRepository = mock(ItemRepository.class);
        ItemStateHistoryRepository historyRepository = mock(ItemStateHistoryRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> {
            Item item = inv.getArgument(0);
            if (item.getId() == null) {
                item.setId(UUID.randomUUID());
            }
            itemStore.put(item.getId(), item);
            return item;
        });
        when(itemRepository.findById(any(UUID.class))).thenAnswer(inv ->
                Optional.ofNullable(itemStore.get(inv.<UUID>getArgument(0))));
        when(itemRepository.findByQrCodeIgnoreCase(any(String.class))).thenAnswer(inv -> {
            String qr = inv.getArgument(0);
            return itemStore.values().stream()
                    .filter(i -> qr.equalsIgnoreCase(i.getQrCode()))
                    .findFirst();
        });
        when(historyRepository.save(any(ItemStateHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        ItemStateService itemStateService = new ItemStateService(itemRepository, historyRepository, eventPublisher);
        QrIdService qrIdService = new QrIdService(itemRepository);
        ItemMapper itemMapper = Mappers.getMapper(ItemMapper.class);
        assetService = new AssetService(itemRepository, historyRepository, itemStateService, qrIdService, itemMapper);

        movementRepository = mock(MovementRepository.class);
        when(movementRepository.save(any(MovementRecord.class))).thenAnswer(inv -> {
            MovementRecord rec = inv.getArgument(0);
            if (rec.getId() == null) {
                rec.setId(UUID.randomUUID());
            }
            movementLedger.add(rec);
            return rec;
        });
        when(movementRepository.findAllByOperationIdOrderByRecordedAtAsc(any(UUID.class))).thenAnswer(inv -> {
            UUID opId = inv.getArgument(0);
            return movementLedger.stream()
                    .filter(r -> opId.equals(r.getOperationId()))
                    .toList();
        });

        totpVerificationApi = (empId, code) -> "654321".equals(code);
        MovementMapper movementMapper = Mappers.getMapper(MovementMapper.class);

        movementService = new MovementService(
                movementRepository,
                assetService,
                totpVerificationApi,
                movementMapper
        );
    }

    @Test
    void issueFlowDispatchesToYoldaAndRecipientConfirmsViaTotpToFoydalanishda() {
        Item item = createWarehouseItem("ABCDEF2345");
        UUID targetBuildingId = UUID.randomUUID();
        UUID targetFloorId = UUID.randomUUID();
        UUID targetRoomId = UUID.randomUUID();
        UUID targetDeptId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        // 1. Warehouse scans QR and dispatches -> item moves OMBORDA -> YOLDA
        MovementRecordDto dispatched = movementService.dispatchIssue(new IssueDispatchRequest(
                null,
                "ABCDEF2345",
                "ISSUE-2026-001",
                targetBuildingId,
                targetFloorId,
                targetRoomId,
                targetDeptId,
                recipientId,
                UUID.randomUUID(),
                "Lab equipment issue"
        ));

        assertThat(dispatched.eventType()).isEqualTo(MovementEventType.ISSUE_DISPATCHED);
        assertThat(item.getState()).isEqualTo(ItemState.YOLDA);

        // 2. Invalid TOTP is rejected
        assertThatThrownBy(() -> movementService.confirmIssue(
                dispatched.operationId(),
                new MovementConfirmRequest(recipientId, "000000", "Wrong TOTP")
        )).isInstanceOf(BadCredentialsException.class);
        assertThat(item.getState()).isEqualTo(ItemState.YOLDA);

        // 3. Recipient confirms with valid TOTP -> item moves YOLDA -> FOYDALANISHDA
        MovementRecordDto confirmed = movementService.confirmIssue(
                dispatched.operationId(),
                new MovementConfirmRequest(recipientId, "654321", "Received in good condition")
        );

        assertThat(confirmed.eventType()).isEqualTo(MovementEventType.ISSUE_ACCEPTED);
        assertThat(confirmed.totpVerified()).isTrue();
        assertThat(item.getState()).isEqualTo(ItemState.FOYDALANISHDA);
        assertThat(item.getRoomId()).isEqualTo(targetRoomId);
        assertThat(item.getCustodianEmployeeId()).isEqualTo(recipientId);

        // Verify insert-only ledger has 2 immutable rows for the operation
        assertThat(movementService.getOperationRecords(dispatched.operationId())).hasSize(2);
    }

    @Test
    void issueRejectionReturnsItemFromYoldaBackToOmborda() {
        Item item = createWarehouseItem("GHIJKL2345");
        UUID recipientId = UUID.randomUUID();

        MovementRecordDto dispatched = movementService.dispatchIssue(new IssueDispatchRequest(
                item.getId(),
                null,
                "ISSUE-2026-002",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                recipientId,
                UUID.randomUUID(),
                null
        ));
        assertThat(item.getState()).isEqualTo(ItemState.YOLDA);

        MovementRecordDto rejected = movementService.rejectIssue(
                dispatched.operationId(),
                new MovementRejectRequest(recipientId, "Wrong model delivered")
        );

        assertThat(rejected.eventType()).isEqualTo(MovementEventType.ISSUE_REJECTED);
        assertThat(item.getState()).isEqualTo(ItemState.OMBORDA);
        assertThat(movementService.getOperationRecords(dispatched.operationId())).hasSize(2);
    }

    @Test
    void transferFlowMovesItemRoomToRoomAndPersonToPersonWithRecipientConfirmationAndRejection() {
        Item item = createWarehouseItem("MNOPQR2345");
        UUID initialRoomId = UUID.randomUUID();
        UUID senderEmployeeId = UUID.randomUUID();
        UUID receiverEmployeeId = UUID.randomUUID();
        UUID newRoomId = UUID.randomUUID();

        // Issue item first so it is in FOYDALANISHDA
        MovementRecordDto issueDispatch = movementService.dispatchIssue(new IssueDispatchRequest(
                item.getId(), null, "ISSUE-INIT", null, null, initialRoomId, null, senderEmployeeId, null, null
        ));
        movementService.confirmIssue(issueDispatch.operationId(), new MovementConfirmRequest(senderEmployeeId, "654321", "ok"));
        assertThat(item.getState()).isEqualTo(ItemState.FOYDALANISHDA);

        // 1. Initiate Room-to-Room / Person-to-Person transfer -> FOYDALANISHDA -> YOLDA
        MovementRecordDto transferInit = movementService.initiateTransfer(new TransferInitiateRequest(
                item.getId(),
                null,
                "TRF-2026-001",
                MovementType.TRANSFER_ROOM_TO_ROOM,
                null,
                null,
                newRoomId,
                null,
                receiverEmployeeId,
                senderEmployeeId,
                "Moving to Room 302"
        ));
        assertThat(transferInit.eventType()).isEqualTo(MovementEventType.TRANSFER_INITIATED);
        assertThat(item.getState()).isEqualTo(ItemState.YOLDA);

        // 2. Receiving party confirms transfer via TOTP -> YOLDA -> FOYDALANISHDA with new room & custodian
        MovementRecordDto transferConfirmed = movementService.confirmTransfer(
                transferInit.operationId(),
                new MovementConfirmRequest(receiverEmployeeId, "654321", "Accepted in Room 302")
        );
        assertThat(transferConfirmed.eventType()).isEqualTo(MovementEventType.TRANSFER_ACCEPTED);
        assertThat(item.getState()).isEqualTo(ItemState.FOYDALANISHDA);
        assertThat(item.getRoomId()).isEqualTo(newRoomId);
        assertThat(item.getCustodianEmployeeId()).isEqualTo(receiverEmployeeId);

        // 3. Initiate a second Person-to-Person transfer and reject it -> restores FOYDALANISHDA with current custodian
        UUID thirdEmployeeId = UUID.randomUUID();
        MovementRecordDto secondTransferInit = movementService.initiateTransfer(new TransferInitiateRequest(
                item.getId(),
                null,
                "TRF-2026-002",
                MovementType.TRANSFER_PERSON_TO_PERSON,
                null,
                null,
                newRoomId,
                null,
                thirdEmployeeId,
                receiverEmployeeId,
                "Handover"
        ));
        assertThat(item.getState()).isEqualTo(ItemState.YOLDA);

        MovementRecordDto secondTransferRejected = movementService.rejectTransfer(
                secondTransferInit.operationId(),
                new MovementRejectRequest(thirdEmployeeId, "Cannot accept custody while on leave")
        );
        assertThat(secondTransferRejected.eventType()).isEqualTo(MovementEventType.TRANSFER_REJECTED);
        assertThat(item.getState()).isEqualTo(ItemState.FOYDALANISHDA);
        assertThat(item.getCustodianEmployeeId()).isEqualTo(receiverEmployeeId);
    }

    private Item createWarehouseItem(String qrCode) {
        Item item = Item.builder()
                .inventoryNumber("INV-" + qrCode)
                .qrCode(qrCode)
                .name("Dell Latitude Laptop")
                .categoryId(UUID.randomUUID())
                .purchasePrice(new BigDecimal("14000000.00"))
                .residualValue(new BigDecimal("14000000.00"))
                .buildingId(UUID.randomUUID())
                .build();
        item.setId(UUID.randomUUID());
        itemStore.put(item.getId(), item);
        return item;
    }
}
