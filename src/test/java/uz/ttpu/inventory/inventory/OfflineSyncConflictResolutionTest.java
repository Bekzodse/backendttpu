package uz.ttpu.inventory.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.inventory.api.OfflineScanActionRequest;
import uz.ttpu.inventory.inventory.api.OfflineScanActionResultDto;
import uz.ttpu.inventory.inventory.api.OfflineSyncBatchRequest;
import uz.ttpu.inventory.inventory.api.OfflineSyncBatchResponseDto;
import uz.ttpu.inventory.inventory.domain.InventoryExpectedItem;
import uz.ttpu.inventory.inventory.domain.InventoryLineStatus;
import uz.ttpu.inventory.inventory.domain.InventoryScanAction;
import uz.ttpu.inventory.inventory.domain.InventorySession;
import uz.ttpu.inventory.inventory.domain.InventorySessionRoom;
import uz.ttpu.inventory.inventory.domain.InventorySessionStatus;
import uz.ttpu.inventory.inventory.domain.ReconciliationCategory;
import uz.ttpu.inventory.inventory.repository.InventoryExpectedItemRepository;
import uz.ttpu.inventory.inventory.repository.InventoryScanActionRepository;
import uz.ttpu.inventory.inventory.repository.InventorySessionRepository;
import uz.ttpu.inventory.inventory.repository.InventorySessionRoomRepository;
import uz.ttpu.inventory.inventory.service.InventoryMapper;
import uz.ttpu.inventory.inventory.service.InventoryService;

class OfflineSyncConflictResolutionTest {

  private InventorySessionRepository sessionRepository;
  private InventorySessionRoomRepository sessionRoomRepository;
  private InventoryExpectedItemRepository expectedItemRepository;
  private InventoryScanActionRepository scanActionRepository;
  private AssetLogisticsApi assetLogisticsApi;
  private SimpMessagingTemplate messagingTemplate;
  private InventoryService inventoryService;

  private final List<InventoryScanAction> storedActions = new ArrayList<>();
  private final List<InventoryExpectedItem> storedExpected = new ArrayList<>();
  private final List<InventorySessionRoom> storedRooms = new ArrayList<>();
  private InventorySession session;

  private final UUID sessionId = UUID.randomUUID();
  private final UUID room1Id = UUID.randomUUID();
  private final UUID room2Id = UUID.randomUUID();
  private final UUID memberAId = UUID.randomUUID();
  private final UUID memberBId = UUID.randomUUID();
  private final UUID itemId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    sessionRepository = mock(InventorySessionRepository.class);
    sessionRoomRepository = mock(InventorySessionRoomRepository.class);
    expectedItemRepository = mock(InventoryExpectedItemRepository.class);
    scanActionRepository = mock(InventoryScanActionRepository.class);
    assetLogisticsApi = mock(AssetLogisticsApi.class);
    messagingTemplate = mock(SimpMessagingTemplate.class);
    InventoryMapper mapper = Mappers.getMapper(InventoryMapper.class);

    storedActions.clear();
    storedExpected.clear();
    storedRooms.clear();

    session =
        InventorySession.builder()
            .sessionCode("INV-2026-TEST")
            .rectorOrderNumber("ORD-101")
            .rectorOrderDate(Instant.now())
            .sessionDate(Instant.now())
            .status(InventorySessionStatus.IN_PROGRESS)
            .chairmanEmployeeId(UUID.randomUUID())
            .commissionMemberIds(List.of(memberAId, memberBId))
            .roomIds(List.of(room1Id, room2Id))
            .packageVersion(1L)
            .build();
    session.setId(sessionId);

    InventorySessionRoom r1 =
        InventorySessionRoom.builder()
            .roomId(room1Id)
            .session(session)
            .expectedCount(1)
            .foundCount(0)
            .notFoundCount(1)
            .completed(false)
            .build();
    r1.setId(UUID.randomUUID());
    storedRooms.add(r1);

    InventorySessionRoom r2 =
        InventorySessionRoom.builder()
            .roomId(room2Id)
            .session(session)
            .expectedCount(0)
            .foundCount(0)
            .notFoundCount(0)
            .completed(false)
            .build();
    r2.setId(UUID.randomUUID());
    storedRooms.add(r2);

    InventoryExpectedItem expectedItem =
        InventoryExpectedItem.builder()
            .session(session)
            .roomId(room1Id)
            .itemId(itemId)
            .inventoryNumber("INV-IT-0001")
            .qrCode("QRIT0001")
            .itemName("ThinkPad Laptop")
            .bookValue(new BigDecimal("1200000.00"))
            .lineStatus(InventoryLineStatus.EXPECTED)
            .build();
    expectedItem.setId(UUID.randomUUID());
    storedExpected.add(expectedItem);

    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
    when(sessionRepository.saveAndFlush(any(InventorySession.class)))
        .thenAnswer(i -> i.getArgument(0));

    when(sessionRoomRepository.findBySessionIdAndRoomId(sessionId, room1Id))
        .thenReturn(Optional.of(r1));
    when(sessionRoomRepository.findBySessionIdAndRoomId(sessionId, room2Id))
        .thenReturn(Optional.of(r2));
    when(sessionRoomRepository.saveAndFlush(any(InventorySessionRoom.class)))
        .thenAnswer(i -> i.getArgument(0));
    when(sessionRoomRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId))
        .thenReturn(storedRooms);

    when(expectedItemRepository.findBySessionIdAndItemId(sessionId, itemId))
        .thenReturn(Optional.of(expectedItem));
    when(expectedItemRepository.findBySessionIdAndQrCodeIgnoreCase(sessionId, "QRIT0001"))
        .thenReturn(Optional.of(expectedItem));
    when(expectedItemRepository.findBySessionIdAndInventoryNumberIgnoreCase(
            sessionId, "INV-IT-0001"))
        .thenReturn(Optional.of(expectedItem));
    when(expectedItemRepository.findAllBySessionIdAndRoomIdOrderByInventoryNumberAsc(
            sessionId, room1Id))
        .thenReturn(List.of(expectedItem));
    when(expectedItemRepository.findAllBySessionIdAndRoomIdOrderByInventoryNumberAsc(
            sessionId, room2Id))
        .thenReturn(List.of());
    when(expectedItemRepository.saveAndFlush(any(InventoryExpectedItem.class)))
        .thenAnswer(i -> i.getArgument(0));

    when(scanActionRepository.findByClientActionId(any(UUID.class)))
        .thenAnswer(
            inv -> {
              UUID cid = inv.getArgument(0);
              return storedActions.stream()
                  .filter(a -> a.getClientActionId().equals(cid))
                  .findFirst();
            });

    when(scanActionRepository.saveAndFlush(any(InventoryScanAction.class)))
        .thenAnswer(
            inv -> {
              InventoryScanAction a = inv.getArgument(0);
              if (a.getId() == null) {
                a.setId(UUID.randomUUID());
              }
              storedActions.add(a);
              return a;
            });

    when(scanActionRepository.findAllBySessionIdAndItemId(any(UUID.class), any(UUID.class)))
        .thenAnswer(
            inv -> {
              UUID id = inv.getArgument(1);
              return storedActions.stream()
                  .filter(a -> a.getItemId() != null && a.getItemId().equals(id))
                  .toList();
            });

    when(scanActionRepository.findAllBySessionIdAndQrCodeIgnoreCase(
            any(UUID.class), any(String.class)))
        .thenAnswer(
            inv -> {
              String qr = inv.getArgument(1);
              return storedActions.stream()
                  .filter(a -> a.getQrCode() != null && a.getQrCode().equalsIgnoreCase(qr))
                  .toList();
            });

    when(scanActionRepository.findAllBySessionIdAndInventoryNumberIgnoreCase(
            any(UUID.class), any(String.class)))
        .thenAnswer(
            inv -> {
              String invNum = inv.getArgument(1);
              return storedActions.stream()
                  .filter(
                      a ->
                          a.getInventoryNumber() != null
                              && a.getInventoryNumber().equalsIgnoreCase(invNum))
                  .toList();
            });

    inventoryService =
        new InventoryService(
            sessionRepository,
            sessionRoomRepository,
            expectedItemRepository,
            scanActionRepository,
            assetLogisticsApi,
            mapper,
            messagingTemplate);
  }

  @Test
  @DisplayName("1. Single scan by Member A in Room 1 applies normally without conflict")
  void singleScan_appliesWithoutConflict() {
    OfflineScanActionRequest action =
        new OfflineScanActionRequest(
            UUID.randomUUID(),
            sessionId,
            room1Id,
            memberAId,
            itemId,
            "QRIT0001",
            "INV-IT-0001",
            "ThinkPad Laptop",
            Instant.now(),
            "Found on table");

    OfflineSyncBatchResponseDto response =
        inventoryService.processOfflineBatch(
            new OfflineSyncBatchRequest(sessionId, List.of(action)));

    assertThat(response.appliedCount()).isEqualTo(1);
    assertThat(response.conflictCount()).isEqualTo(0);
    assertThat(response.duplicateIgnoredCount()).isEqualTo(0);

    OfflineScanActionResultDto result = response.results().get(0);
    assertThat(result.conflict()).isFalse();
    assertThat(result.conflictDetails()).isNull();
    assertThat(result.category()).isEqualTo(ReconciliationCategory.JOYIDA);
  }

  @Test
  @DisplayName(
      "2. Conflict Resolution: Member B scanning same item in Room 2 is marked as a CONFLICT")
  void conflictDetected_whenDifferentMemberScansSameItemInDifferentRoom() {
    // Member A scans in Room 1
    OfflineScanActionRequest actionA =
        new OfflineScanActionRequest(
            UUID.randomUUID(),
            sessionId,
            room1Id,
            memberAId,
            itemId,
            "QRIT0001",
            "INV-IT-0001",
            "ThinkPad Laptop",
            Instant.now().minusSeconds(10),
            "Scanned by A in Room 1");

    inventoryService.processOfflineBatch(new OfflineSyncBatchRequest(sessionId, List.of(actionA)));

    // Member B scans SAME item in Room 2
    OfflineScanActionRequest actionB =
        new OfflineScanActionRequest(
            UUID.randomUUID(),
            sessionId,
            room2Id, // Different room
            memberBId, // Different commission member
            itemId,
            "QRIT0001",
            "INV-IT-0001",
            "ThinkPad Laptop",
            Instant.now(),
            "Scanned by B in Room 2");

    OfflineSyncBatchResponseDto responseB =
        inventoryService.processOfflineBatch(
            new OfflineSyncBatchRequest(sessionId, List.of(actionB)));

    assertThat(responseB.appliedCount()).isEqualTo(1);
    assertThat(responseB.conflictCount()).isEqualTo(1);

    OfflineScanActionResultDto resultB = responseB.results().get(0);
    assertThat(resultB.conflict()).isTrue();
    assertThat(resultB.conflictDetails())
        .contains("Conflict detected")
        .contains("INV-IT-0001")
        .contains(memberAId.toString())
        .contains(room1Id.toString())
        .contains(memberBId.toString())
        .contains(room2Id.toString());
  }

  @Test
  @DisplayName("3. Same member scanning same item in same room again is NOT marked as conflict")
  void sameMemberSameRoom_notAConflict() {
    OfflineScanActionRequest action1 =
        new OfflineScanActionRequest(
            UUID.randomUUID(),
            sessionId,
            room1Id,
            memberAId,
            itemId,
            "QRIT0001",
            "INV-IT-0001",
            "ThinkPad Laptop",
            Instant.now().minusSeconds(5),
            "First scan");
    inventoryService.processOfflineBatch(new OfflineSyncBatchRequest(sessionId, List.of(action1)));

    OfflineScanActionRequest action2 =
        new OfflineScanActionRequest(
            UUID.randomUUID(),
            sessionId,
            room1Id,
            memberAId,
            itemId,
            "QRIT0001",
            "INV-IT-0001",
            "ThinkPad Laptop",
            Instant.now(),
            "Second scan by same member in same room");
    OfflineSyncBatchResponseDto response =
        inventoryService.processOfflineBatch(
            new OfflineSyncBatchRequest(sessionId, List.of(action2)));

    assertThat(response.appliedCount()).isEqualTo(1);
    assertThat(response.conflictCount()).isEqualTo(0);
    assertThat(response.results().get(0).conflict()).isFalse();
  }

  @Test
  @DisplayName("4. Submitting identical clientActionId is ignored idempotently")
  void duplicateClientActionId_ignoredIdempotently() {
    UUID clientActionId = UUID.randomUUID();
    OfflineScanActionRequest action =
        new OfflineScanActionRequest(
            clientActionId,
            sessionId,
            room1Id,
            memberAId,
            itemId,
            "QRIT0001",
            "INV-IT-0001",
            "ThinkPad Laptop",
            Instant.now(),
            "Initial scan");

    OfflineSyncBatchResponseDto res1 =
        inventoryService.processOfflineBatch(
            new OfflineSyncBatchRequest(sessionId, List.of(action)));
    assertThat(res1.appliedCount()).isEqualTo(1);
    assertThat(res1.duplicateIgnoredCount()).isEqualTo(0);

    // Resubmit exact same clientActionId
    OfflineSyncBatchResponseDto res2 =
        inventoryService.processOfflineBatch(
            new OfflineSyncBatchRequest(sessionId, List.of(action)));
    assertThat(res2.appliedCount()).isEqualTo(0);
    assertThat(res2.duplicateIgnoredCount()).isEqualTo(1);
    assertThat(res2.results().get(0).duplicateIgnored()).isTrue();
  }

  @Test
  @DisplayName("5. Both conflicting scans inside the same batch are detected and flagged")
  void conflictDetected_withinSameBatchPayload() {
    OfflineScanActionRequest scanA =
        new OfflineScanActionRequest(
            UUID.randomUUID(),
            sessionId,
            room1Id,
            memberAId,
            itemId,
            "QRIT0001",
            "INV-IT-0001",
            "ThinkPad Laptop",
            Instant.now().minusSeconds(2),
            "Batch action 1");

    OfflineScanActionRequest scanB =
        new OfflineScanActionRequest(
            UUID.randomUUID(),
            sessionId,
            room2Id,
            memberBId,
            itemId,
            "QRIT0001",
            "INV-IT-0001",
            "ThinkPad Laptop",
            Instant.now(),
            "Batch action 2");

    OfflineSyncBatchResponseDto response =
        inventoryService.processOfflineBatch(
            new OfflineSyncBatchRequest(sessionId, List.of(scanA, scanB)));

    assertThat(response.totalSubmitted()).isEqualTo(2);
    assertThat(response.appliedCount()).isEqualTo(2);
    assertThat(response.conflictCount()).isEqualTo(1);

    assertThat(response.results().get(0).conflict()).isFalse();
    assertThat(response.results().get(1).conflict()).isTrue();
    assertThat(response.results().get(1).conflictDetails()).contains("Conflict detected");
  }
}
