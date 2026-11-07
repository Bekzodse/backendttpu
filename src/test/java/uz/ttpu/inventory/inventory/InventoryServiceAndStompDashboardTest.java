package uz.ttpu.inventory.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.asset.domain.ItemState;
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
import uz.ttpu.inventory.inventory.api.RoomProgressDto;
import uz.ttpu.inventory.inventory.api.ScanInventoryItemRequest;
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

class InventoryServiceAndStompDashboardTest {

  private InventorySessionRepository sessionRepository;
  private InventorySessionRoomRepository sessionRoomRepository;
  private InventoryExpectedItemRepository expectedItemRepository;
  private InventoryScanActionRepository scanActionRepository;
  private AssetLogisticsApi assetLogisticsApi;
  private SimpMessagingTemplate messagingTemplate;
  private InventoryService inventoryService;

  private final List<InventorySessionRoom> storedRooms = new ArrayList<>();
  private final List<InventoryExpectedItem> storedExpectedItems = new ArrayList<>();
  private final List<InventoryScanAction> storedScanActions = new ArrayList<>();
  private InventorySession storedSession;

  @BeforeEach
  void setUp() {
    sessionRepository = mock(InventorySessionRepository.class);
    sessionRoomRepository = mock(InventorySessionRoomRepository.class);
    expectedItemRepository = mock(InventoryExpectedItemRepository.class);
    scanActionRepository = mock(InventoryScanActionRepository.class);
    assetLogisticsApi = mock(AssetLogisticsApi.class);
    messagingTemplate = mock(SimpMessagingTemplate.class);

    storedRooms.clear();
    storedExpectedItems.clear();
    storedScanActions.clear();
    storedSession = null;

    when(sessionRepository.save(any(InventorySession.class)))
        .thenAnswer(
            inv -> {
              InventorySession s = inv.getArgument(0);
              if (s.getId() == null) {
                s.setId(UUID.randomUUID());
              }
              storedSession = s;
              syncStoredChildren(s);
              return s;
            });

    when(sessionRepository.saveAndFlush(any(InventorySession.class)))
        .thenAnswer(
            inv -> {
              InventorySession s = inv.getArgument(0);
              if (s.getId() == null) {
                s.setId(UUID.randomUUID());
              }
              storedSession = s;
              syncStoredChildren(s);
              return s;
            });

    when(sessionRepository.findById(any(UUID.class)))
        .thenAnswer(
            inv -> {
              UUID id = inv.getArgument(0);
              if (storedSession != null && storedSession.getId().equals(id)) {
                return Optional.of(storedSession);
              }
              return Optional.empty();
            });

    when(sessionRoomRepository.findAllBySessionIdOrderByCreatedAtAsc(any(UUID.class)))
        .thenAnswer(inv -> new ArrayList<>(storedRooms));

    when(sessionRoomRepository.findBySessionIdAndRoomId(any(UUID.class), any(UUID.class)))
        .thenAnswer(
            inv -> {
              UUID roomId = inv.getArgument(1);
              return storedRooms.stream().filter(r -> r.getRoomId().equals(roomId)).findFirst();
            });

    when(sessionRoomRepository.saveAndFlush(any(InventorySessionRoom.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    when(expectedItemRepository.findAllBySessionIdOrderByInventoryNumberAsc(any(UUID.class)))
        .thenAnswer(inv -> new ArrayList<>(storedExpectedItems));

    when(expectedItemRepository.findAllBySessionIdAndRoomIdOrderByInventoryNumberAsc(
            any(UUID.class), any(UUID.class)))
        .thenAnswer(
            inv -> {
              UUID roomId = inv.getArgument(1);
              return storedExpectedItems.stream()
                  .filter(i -> i.getRoomId().equals(roomId))
                  .toList();
            });

    when(expectedItemRepository.findBySessionIdAndQrCodeIgnoreCase(
            any(UUID.class), any(String.class)))
        .thenAnswer(
            inv -> {
              String qr = inv.getArgument(1);
              return storedExpectedItems.stream()
                  .filter(i -> i.getQrCode().equalsIgnoreCase(qr))
                  .findFirst();
            });

    when(expectedItemRepository.saveAndFlush(any(InventoryExpectedItem.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(expectedItemRepository.save(any(InventoryExpectedItem.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    when(scanActionRepository.findByClientActionId(any(UUID.class)))
        .thenAnswer(
            inv -> {
              UUID clientId = inv.getArgument(0);
              return storedScanActions.stream()
                  .filter(a -> a.getClientActionId().equals(clientId))
                  .findFirst();
            });
    when(scanActionRepository.saveAndFlush(any(InventoryScanAction.class)))
        .thenAnswer(
            inv -> {
              InventoryScanAction action = inv.getArgument(0);
              if (action.getId() == null) {
                action.setId(UUID.randomUUID());
              }
              storedScanActions.add(action);
              return action;
            });
    when(scanActionRepository.findAllBySessionIdAndReconciliationCategoryOrderByScannedAtAsc(
            any(UUID.class), any(ReconciliationCategory.class)))
        .thenAnswer(
            inv -> {
              ReconciliationCategory cat = inv.getArgument(1);
              return storedScanActions.stream()
                  .filter(a -> a.getReconciliationCategory() == cat)
                  .toList();
            });

    InventoryMapper mapper = Mappers.getMapper(InventoryMapper.class);
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

  private void syncStoredChildren(InventorySession s) {
    storedRooms.clear();
    for (InventorySessionRoom r : s.getSessionRooms()) {
      if (r.getId() == null) {
        r.setId(UUID.randomUUID());
      }
      storedRooms.add(r);
    }
    storedExpectedItems.clear();
    for (InventoryExpectedItem i : s.getExpectedItems()) {
      if (i.getId() == null) {
        i.setId(UUID.randomUUID());
      }
      storedExpectedItems.add(i);
    }
  }

  @Test
  void generatesExpectedListPerRoomScansItemsAndPushesLiveStompDashboardUpdates() {
    UUID room1Id = UUID.randomUUID();
    UUID room2Id = UUID.randomUUID();
    UUID chairmanId = UUID.randomUUID();
    UUID member1Id = UUID.randomUUID();
    UUID member2Id = UUID.randomUUID();

    ItemDto item1 =
        new ItemDto(
            UUID.randomUUID(),
            "INV-101",
            "SN-101",
            "ABCDEF2345",
            "Dell OptiPlex 7010",
            "Lab PC 1",
            UUID.randomUUID(),
            ItemState.FOYDALANISHDA,
            new BigDecimal("12000000.00"),
            new BigDecimal("10000000.00"),
            Instant.now(),
            null,
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            room1Id,
            UUID.randomUUID(),
            member1Id,
            Map.of(),
            "1C-101",
            Instant.now(),
            Instant.now());
    ItemDto item2 =
        new ItemDto(
            UUID.randomUUID(),
            "INV-102",
            "SN-102",
            "BCDEFG2345",
            "Dell Monitor 27",
            "Lab Monitor 1",
            UUID.randomUUID(),
            ItemState.FOYDALANISHDA,
            new BigDecimal("4000000.00"),
            new BigDecimal("3500000.00"),
            Instant.now(),
            null,
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            room1Id,
            UUID.randomUUID(),
            member1Id,
            Map.of(),
            "1C-102",
            Instant.now(),
            Instant.now());
    ItemDto item3 =
        new ItemDto(
            UUID.randomUUID(),
            "INV-201",
            "SN-201",
            "CDEFGH2345",
            "Smart Projector Epson",
            "Projector Room 2",
            UUID.randomUUID(),
            ItemState.FOYDALANISHDA,
            new BigDecimal("18000000.00"),
            new BigDecimal("15000000.00"),
            Instant.now(),
            null,
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            room2Id,
            UUID.randomUUID(),
            member2Id,
            Map.of(),
            "1C-201",
            Instant.now(),
            Instant.now());

    when(assetLogisticsApi.findExpectedItemsForRooms(any()))
        .thenReturn(List.of(item1, item2, item3));

    // 1. Create Inventory Session with Rector's Order, Date, Commission Members, and Rooms
    InventorySessionDto session =
        inventoryService.createSession(
            new CreateInventorySessionRequest(
                "INV-SES-2026-Q4",
                "RECTOR-ORD-77/2026",
                Instant.parse("2026-11-01T09:00:00Z"),
                Instant.parse("2026-11-06T09:00:00Z"),
                chairmanId,
                List.of(member1Id, member2Id),
                List.of(room1Id, room2Id),
                "Annual Q4 university-wide inventory check",
                true));

    assertThat(session.status()).isEqualTo(InventorySessionStatus.IN_PROGRESS);
    assertThat(session.rectorOrderNumber()).isEqualTo("RECTOR-ORD-77/2026");
    assertThat(session.commissionMemberIds()).containsExactly(member1Id, member2Id);
    assertThat(session.roomIds()).containsExactly(room1Id, room2Id);

    // 2. Verify Expected Lists generated per room
    List<RoomExpectedItemsDto> expectedByRoom =
        inventoryService.getExpectedListsByRoom(session.id());
    assertThat(expectedByRoom).hasSize(2);
    RoomExpectedItemsDto room1Expected = expectedByRoom.get(0);
    assertThat(room1Expected.roomId()).isEqualTo(room1Id);
    assertThat(room1Expected.expectedCount()).isEqualTo(2);
    assertThat(room1Expected.foundCount()).isEqualTo(0);
    assertThat(room1Expected.notFoundCount()).isEqualTo(2);

    RoomExpectedItemsDto room2Expected = expectedByRoom.get(1);
    assertThat(room2Expected.roomId()).isEqualTo(room2Id);
    assertThat(room2Expected.expectedCount()).isEqualTo(1);
    assertThat(room2Expected.notFoundCount()).isEqualTo(1);

    // 3. Scan item1 in room1 -> foundCount=1, notFoundCount=1 in room1
    InventoryExpectedItemDto scanned1 =
        inventoryService.scanItem(
            session.id(),
            new ScanInventoryItemRequest(
                null, "ABCDEF2345", room1Id, member1Id, "Verified on desk 1"));
    assertThat(scanned1.lineStatus()).isEqualTo(InventoryLineStatus.FOUND);

    // 4. Complete room1 while item2 is still un-scanned -> item2 becomes MISSING, room1 is
    // completed with notFoundCount=1
    RoomProgressDto room1Progress = inventoryService.completeRoom(session.id(), room1Id);
    assertThat(room1Progress.completed()).isTrue();
    assertThat(room1Progress.expectedCount()).isEqualTo(2);
    assertThat(room1Progress.foundCount()).isEqualTo(1);
    assertThat(room1Progress.notFoundCount()).isEqualTo(1);
    assertThat(room1Progress.completionPercentage()).isEqualByComparingTo("50.00");

    // 5. Scan item3 in room2 -> room2 has 1/1 found, automatically completes room2 and session!
    InventoryExpectedItemDto scanned3 =
        inventoryService.scanItem(
            session.id(),
            new ScanInventoryItemRequest(null, "CDEFGH2345", room2Id, member2Id, "Ceiling mount"));
    assertThat(scanned3.lineStatus()).isEqualTo(InventoryLineStatus.FOUND);

    InventoryProgressDashboardDto finalDashboard =
        inventoryService.getProgressDashboard(session.id());
    assertThat(finalDashboard.status()).isEqualTo(InventorySessionStatus.COMPLETED);
    assertThat(finalDashboard.totalRooms()).isEqualTo(2);
    assertThat(finalDashboard.completedRooms()).isEqualTo(2);
    assertThat(finalDashboard.totalExpectedItems()).isEqualTo(3);
    assertThat(finalDashboard.totalFoundItems()).isEqualTo(2);
    assertThat(finalDashboard.totalNotFoundItems()).isEqualTo(1);
    assertThat(finalDashboard.overallCompletionPercentage()).isEqualByComparingTo("66.67");

    // 6. Verify STOMP WebSocket broadcasts occurred on session progress topic and global dashboard
    // topic
    String sessionTopic =
        InventoryService.TOPIC_SESSION_PROGRESS_PREFIX
            + session.id()
            + InventoryService.TOPIC_SESSION_PROGRESS_SUFFIX;
    ArgumentCaptor<InventoryProgressDashboardDto> dashboardCaptor =
        ArgumentCaptor.forClass(InventoryProgressDashboardDto.class);
    verify(messagingTemplate, atLeastOnce())
        .convertAndSend(eq(sessionTopic), dashboardCaptor.capture());
    verify(messagingTemplate, atLeastOnce())
        .convertAndSend(
            eq(InventoryService.TOPIC_GLOBAL_DASHBOARD), any(InventoryProgressDashboardDto.class));

    InventoryProgressDashboardDto lastPushed = dashboardCaptor.getValue();
    assertThat(lastPushed.completedRooms()).isEqualTo(2);
    assertThat(lastPushed.totalNotFoundItems()).isEqualTo(1);
  }

  @Test
  void
      offlineSyncPackageBatchIdempotencyAndReconciliationCategorizesAllFourUzbekStatusesAndMovesMissingToQidiruvda() {
    UUID room1Id = UUID.randomUUID();
    UUID room2Id = UUID.randomUUID();
    UUID chairmanId = UUID.randomUUID();
    UUID scannerId = UUID.randomUUID();

    UUID itemInPlaceId = UUID.randomUUID();
    UUID itemAnotherRoomId = UUID.randomUUID();
    UUID itemMissingId = UUID.randomUUID();
    UUID itemSurplusId = UUID.randomUUID();

    ItemDto itemInPlace =
        new ItemDto(
            itemInPlaceId,
            "INV-301",
            "SN-301",
            "QR301ABCDE",
            "HP Workstation Z4",
            "In-place PC",
            UUID.randomUUID(),
            ItemState.FOYDALANISHDA,
            new BigDecimal("22000000.00"),
            new BigDecimal("20000000.00"),
            Instant.now(),
            null,
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            room1Id,
            UUID.randomUUID(),
            scannerId,
            Map.of(),
            "1C-301",
            Instant.now(),
            Instant.now());
    ItemDto itemAnotherRoom =
        new ItemDto(
            itemAnotherRoomId,
            "INV-302",
            "SN-302",
            "QR302ABCDE",
            "Lenovo ThinkPad T14",
            "Misplaced Laptop",
            UUID.randomUUID(),
            ItemState.FOYDALANISHDA,
            new BigDecimal("15000000.00"),
            new BigDecimal("14000000.00"),
            Instant.now(),
            null,
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            room1Id,
            UUID.randomUUID(),
            scannerId,
            Map.of(),
            "1C-302",
            Instant.now(),
            Instant.now());
    ItemDto itemMissing =
        new ItemDto(
            itemMissingId,
            "INV-303",
            "SN-303",
            "QR303ABCDE",
            "Digital Multimeter Fluke",
            "Missing Meter",
            UUID.randomUUID(),
            ItemState.FOYDALANISHDA,
            new BigDecimal("5000000.00"),
            new BigDecimal("4500000.00"),
            Instant.now(),
            null,
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            room2Id,
            UUID.randomUUID(),
            scannerId,
            Map.of(),
            "1C-303",
            Instant.now(),
            Instant.now());
    ItemDto itemSurplus =
        new ItemDto(
            itemSurplusId,
            "INV-999",
            "SN-999",
            "QR999SURPL",
            "Unlisted Lab Chair",
            "Surplus item from Room 9",
            UUID.randomUUID(),
            ItemState.FOYDALANISHDA,
            new BigDecimal("1500000.00"),
            new BigDecimal("1200000.00"),
            Instant.now(),
            null,
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            scannerId,
            Map.of(),
            "1C-999",
            Instant.now(),
            Instant.now());

    when(assetLogisticsApi.findExpectedItemsForRooms(any()))
        .thenReturn(List.of(itemInPlace, itemAnotherRoom, itemMissing));
    when(assetLogisticsApi.findItemByIdOrQrOptional(any(), eq("QR999SURPL")))
        .thenReturn(Optional.of(itemSurplus));

    InventorySessionDto session =
        inventoryService.createSession(
            new CreateInventorySessionRequest(
                "INV-OFFLINE-2026",
                "RECTOR-ORD-88/2026",
                Instant.now(),
                Instant.now(),
                chairmanId,
                List.of(chairmanId, scannerId),
                List.of(room1Id, room2Id),
                "Offline Sync & Reconciliation Test",
                true));

    // 1. GET /api/v1/sync/sessions/{id}/package returns rooms, expected items, and version number
    OfflineSessionPackageDto offlinePkg = inventoryService.getOfflineSessionPackage(session.id());
    assertThat(offlinePkg.sessionId()).isEqualTo(session.id());
    assertThat(offlinePkg.version()).isGreaterThanOrEqualTo(2L);
    assertThat(offlinePkg.rooms()).hasSize(2);
    assertThat(offlinePkg.expectedItems()).hasSize(3);

    // 2. POST /api/v1/sync/batch with idempotent client UUIDs
    UUID clientUuid1 = UUID.randomUUID(); // Joyida (Room 1 expected, Room 1 scanned)
    UUID clientUuid2 = UUID.randomUUID(); // Boshqa xonada topildi (Room 1 expected, Room 2 scanned)
    UUID clientUuid3 = UUID.randomUUID(); // Ortiqcha (Surplus item not in expected list)

    OfflineSyncBatchResponseDto batchResponse =
        inventoryService.processOfflineBatch(
            new OfflineSyncBatchRequest(
                session.id(),
                List.of(
                    new OfflineScanActionRequest(
                        clientUuid1,
                        session.id(),
                        room1Id,
                        scannerId,
                        null,
                        "QR301ABCDE",
                        null,
                        null,
                        Instant.now(),
                        "In place"),
                    new OfflineScanActionRequest(
                        clientUuid1,
                        session.id(),
                        room1Id,
                        scannerId,
                        null,
                        "QR301ABCDE",
                        null,
                        null,
                        Instant.now(),
                        "Duplicate retry!"),
                    new OfflineScanActionRequest(
                        clientUuid2,
                        session.id(),
                        room2Id,
                        scannerId,
                        null,
                        "QR302ABCDE",
                        null,
                        null,
                        Instant.now(),
                        "Found in Room 2"),
                    new OfflineScanActionRequest(
                        clientUuid3,
                        session.id(),
                        room1Id,
                        scannerId,
                        null,
                        "QR999SURPL",
                        null,
                        null,
                        Instant.now(),
                        "Surplus item in Room 1"))));

    assertThat(batchResponse.totalSubmitted()).isEqualTo(4);
    assertThat(batchResponse.appliedCount()).isEqualTo(3);
    assertThat(batchResponse.duplicateIgnoredCount()).isEqualTo(1);
    assertThat(batchResponse.results().get(1).duplicateIgnored()).isTrue();
    assertThat(batchResponse.results().get(0).category()).isEqualTo(ReconciliationCategory.JOYIDA);
    assertThat(batchResponse.results().get(0).categoryLabel()).isEqualTo("Joyida");
    assertThat(batchResponse.results().get(2).category())
        .isEqualTo(ReconciliationCategory.BOSHQA_XONADA_TOPILDI);
    assertThat(batchResponse.results().get(2).categoryLabel()).isEqualTo("Boshqa xonada topildi");
    assertThat(batchResponse.results().get(3).category())
        .isEqualTo(ReconciliationCategory.ORTIQCHA);
    assertThat(batchResponse.results().get(3).categoryLabel()).isEqualTo("Ortiqcha");

    // 3. Reconciliation: categorizes into Joyida, Boshqa xonada topildi, Topilmadi (-> QIDIRUVDA),
    // and Ortiqcha
    InventoryReconciliationReportDto report = inventoryService.reconcileSession(session.id());
    assertThat(report.sessionStatus()).isEqualTo(InventorySessionStatus.COMPLETED);
    assertThat(report.joyidaCount()).isEqualTo(1);
    assertThat(report.boshqaXonadaTopildiCount()).isEqualTo(1);
    assertThat(report.topilmadiCount()).isEqualTo(1);
    assertThat(report.ortiqchaCount()).isEqualTo(1);

    assertThat(report.joyida().get(0).categoryLabel()).isEqualTo("Joyida");
    assertThat(report.boshqaXonadaTopildi().get(0).categoryLabel())
        .isEqualTo("Boshqa xonada topildi");
    assertThat(report.topilmadi().get(0).categoryLabel()).isEqualTo("Topilmadi");
    assertThat(report.topilmadi().get(0).itemId()).isEqualTo(itemMissingId);
    assertThat(report.topilmadi().get(0).movedToQidiruvda()).isTrue();
    assertThat(report.ortiqcha().get(0).categoryLabel()).isEqualTo("Ortiqcha");
    assertThat(report.ortiqcha().get(0).itemId()).isEqualTo(itemSurplusId);

    // Verify missing item was transitioned to QIDIRUVDA via AssetLogisticsApi
    verify(assetLogisticsApi).markItemMissingDuringInventory(itemMissingId, "INV-OFFLINE-2026");
  }
}
