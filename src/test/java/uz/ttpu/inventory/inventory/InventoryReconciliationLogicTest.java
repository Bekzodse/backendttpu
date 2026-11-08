package uz.ttpu.inventory.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.inventory.api.CursorPageResponse;
import uz.ttpu.inventory.inventory.api.InventoryExpectedItemDto;
import uz.ttpu.inventory.inventory.api.InventoryReconciliationReportDto;
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

class InventoryReconciliationLogicTest {

  private InventorySessionRepository sessionRepository;
  private InventorySessionRoomRepository sessionRoomRepository;
  private InventoryExpectedItemRepository expectedItemRepository;
  private InventoryScanActionRepository scanActionRepository;
  private AssetLogisticsApi assetLogisticsApi;
  private SimpMessagingTemplate messagingTemplate;
  private InventoryService inventoryService;

  private final UUID sessionId = UUID.randomUUID();
  private final UUID room1Id = UUID.randomUUID();
  private final UUID room2Id = UUID.randomUUID();
  private final UUID memberId = UUID.randomUUID();

  private final UUID itemJoyidaId = UUID.randomUUID();
  private final UUID itemMisplacedId = UUID.randomUUID();
  private final UUID itemMissingId = UUID.randomUUID();
  private final UUID itemSurplusId = UUID.randomUUID();

  private final List<InventoryExpectedItem> expectedItems = new ArrayList<>();
  private final List<InventoryScanAction> surplusActions = new ArrayList<>();
  private final List<InventorySessionRoom> rooms = new ArrayList<>();
  private InventorySession session;

  @BeforeEach
  void setUp() {
    sessionRepository = mock(InventorySessionRepository.class);
    sessionRoomRepository = mock(InventorySessionRoomRepository.class);
    expectedItemRepository = mock(InventoryExpectedItemRepository.class);
    scanActionRepository = mock(InventoryScanActionRepository.class);
    assetLogisticsApi = mock(AssetLogisticsApi.class);
    messagingTemplate = mock(SimpMessagingTemplate.class);
    InventoryMapper mapper = Mappers.getMapper(InventoryMapper.class);

    expectedItems.clear();
    surplusActions.clear();
    rooms.clear();

    session =
        InventorySession.builder()
            .sessionCode("INV-RECON-01")
            .rectorOrderNumber("ORD-999")
            .rectorOrderDate(Instant.now())
            .sessionDate(Instant.now())
            .status(InventorySessionStatus.IN_PROGRESS)
            .chairmanEmployeeId(UUID.randomUUID())
            .commissionMemberIds(List.of(memberId))
            .roomIds(List.of(room1Id, room2Id))
            .packageVersion(1L)
            .build();
    session.setId(sessionId);

    // 1. Item found in its expected room (Joyida)
    InventoryExpectedItem item1 =
        InventoryExpectedItem.builder()
            .session(session)
            .roomId(room1Id)
            .itemId(itemJoyidaId)
            .inventoryNumber("INV-001")
            .qrCode("QR001")
            .itemName("Monitor HP")
            .bookValue(new BigDecimal("800000.00"))
            .lineStatus(InventoryLineStatus.FOUND)
            .scannedRoomId(room1Id)
            .scannedByEmployeeId(memberId)
            .scannedAt(Instant.now())
            .build();
    item1.setId(UUID.randomUUID());
    expectedItems.add(item1);

    // 2. Item found in different room (Boshqa xonada topildi)
    InventoryExpectedItem item2 =
        InventoryExpectedItem.builder()
            .session(session)
            .roomId(room1Id)
            .itemId(itemMisplacedId)
            .inventoryNumber("INV-002")
            .qrCode("QR002")
            .itemName("Projector Epson")
            .bookValue(new BigDecimal("3500000.00"))
            .lineStatus(InventoryLineStatus.MISPLACED_FOUND)
            .scannedRoomId(room2Id)
            .scannedByEmployeeId(memberId)
            .scannedAt(Instant.now())
            .build();
    item2.setId(UUID.randomUUID());
    expectedItems.add(item2);

    // 3. Item not scanned (Topilmadi)
    InventoryExpectedItem item3 =
        InventoryExpectedItem.builder()
            .session(session)
            .roomId(room1Id)
            .itemId(itemMissingId)
            .inventoryNumber("INV-003")
            .qrCode("QR003")
            .itemName("Tablet iPad")
            .bookValue(new BigDecimal("2100000.00"))
            .lineStatus(InventoryLineStatus.EXPECTED)
            .build();
    item3.setId(UUID.randomUUID());
    expectedItems.add(item3);

    // 4. Surplus scan action (Ortiqcha)
    InventoryScanAction surplus =
        InventoryScanAction.builder()
            .clientActionId(UUID.randomUUID())
            .session(session)
            .scannedRoomId(room2Id)
            .scannedByEmployeeId(memberId)
            .expectedRoomId(null)
            .itemId(itemSurplusId)
            .qrCode("QRSURPLUS")
            .inventoryNumber("INV-SURPLUS-01")
            .itemName("Extra Laser Printer")
            .scannedAt(Instant.now())
            .reconciliationCategory(ReconciliationCategory.ORTIQCHA)
            .build();
    surplus.setId(UUID.randomUUID());
    surplusActions.add(surplus);

    InventorySessionRoom r1 =
        InventorySessionRoom.builder()
            .session(session)
            .roomId(room1Id)
            .expectedCount(3)
            .foundCount(2)
            .notFoundCount(1)
            .completed(false)
            .build();
    r1.setId(UUID.randomUUID());
    rooms.add(r1);

    InventorySessionRoom r2 =
        InventorySessionRoom.builder()
            .session(session)
            .roomId(room2Id)
            .expectedCount(0)
            .foundCount(0)
            .notFoundCount(0)
            .completed(false)
            .build();
    r2.setId(UUID.randomUUID());
    rooms.add(r2);

    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
    when(sessionRepository.saveAndFlush(any(InventorySession.class)))
        .thenAnswer(i -> i.getArgument(0));

    when(expectedItemRepository.findAllBySessionIdOrderByInventoryNumberAsc(sessionId))
        .thenReturn(expectedItems);
    when(expectedItemRepository.findAllBySessionIdAndRoomIdOrderByInventoryNumberAsc(
            sessionId, room1Id))
        .thenReturn(expectedItems);
    when(expectedItemRepository.findAllBySessionIdAndRoomIdOrderByInventoryNumberAsc(
            sessionId, room2Id))
        .thenReturn(List.of());

    when(scanActionRepository.findAllBySessionIdAndReconciliationCategoryOrderByScannedAtAsc(
            sessionId, ReconciliationCategory.ORTIQCHA))
        .thenReturn(surplusActions);

    when(sessionRoomRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(rooms);
    when(sessionRoomRepository.findBySessionIdAndRoomId(sessionId, room1Id))
        .thenReturn(Optional.of(r1));
    when(sessionRoomRepository.findBySessionIdAndRoomId(sessionId, room2Id))
        .thenReturn(Optional.of(r2));
    when(sessionRoomRepository.saveAndFlush(any(InventorySessionRoom.class)))
        .thenAnswer(i -> i.getArgument(0));

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
  @DisplayName(
      "1. Reconciliation categorizes all items strictly into 4 categories (Joyida, Boshqa xonada"
          + " topildi, Topilmadi, Ortiqcha)")
  void reconcileSession_categorizesIntoFourStrictBuckets() {
    InventoryReconciliationReportDto report = inventoryService.reconcileSession(sessionId);

    assertThat(report.sessionCode()).isEqualTo("INV-RECON-01");
    assertThat(report.sessionStatus()).isEqualTo(InventorySessionStatus.COMPLETED);

    // 1. Joyida
    assertThat(report.joyidaCount()).isEqualTo(1);
    assertThat(report.joyida()).hasSize(1);
    assertThat(report.joyida().get(0).itemId()).isEqualTo(itemJoyidaId);
    assertThat(report.joyida().get(0).category()).isEqualTo(ReconciliationCategory.JOYIDA);
    assertThat(report.joyida().get(0).categoryLabel()).isEqualTo("Joyida");
    verify(assetLogisticsApi).markItemFoundDuringInventory(itemJoyidaId, room1Id, "INV-RECON-01");

    // 2. Boshqa xonada topildi
    assertThat(report.boshqaXonadaTopildiCount()).isEqualTo(1);
    assertThat(report.boshqaXonadaTopildi()).hasSize(1);
    assertThat(report.boshqaXonadaTopildi().get(0).itemId()).isEqualTo(itemMisplacedId);
    assertThat(report.boshqaXonadaTopildi().get(0).category())
        .isEqualTo(ReconciliationCategory.BOSHQA_XONADA_TOPILDI);
    assertThat(report.boshqaXonadaTopildi().get(0).categoryLabel())
        .isEqualTo("Boshqa xonada topildi");
    verify(assetLogisticsApi)
        .markItemFoundDuringInventory(itemMisplacedId, room2Id, "INV-RECON-01");

    // 3. Topilmadi (automatically transitioned to missing / QIDIRUVDA)
    assertThat(report.topilmadiCount()).isEqualTo(1);
    assertThat(report.topilmadi()).hasSize(1);
    assertThat(report.topilmadi().get(0).itemId()).isEqualTo(itemMissingId);
    assertThat(report.topilmadi().get(0).category()).isEqualTo(ReconciliationCategory.TOPILMADI);
    assertThat(report.topilmadi().get(0).categoryLabel()).isEqualTo("Topilmadi");
    assertThat(report.topilmadi().get(0).movedToQidiruvda()).isTrue();
    verify(assetLogisticsApi).markItemMissingDuringInventory(itemMissingId, "INV-RECON-01");

    // 4. Ortiqcha
    assertThat(report.ortiqchaCount()).isEqualTo(1);
    assertThat(report.ortiqcha()).hasSize(1);
    assertThat(report.ortiqcha().get(0).itemId()).isEqualTo(itemSurplusId);
    assertThat(report.ortiqcha().get(0).category()).isEqualTo(ReconciliationCategory.ORTIQCHA);
    assertThat(report.ortiqcha().get(0).categoryLabel()).isEqualTo("Ortiqcha");
  }

  @Test
  @DisplayName("2. Cursor-based pagination returns windowed items and opaque base64 cursor")
  void cursorPagination_returnsWindowAndNextCursor() {
    when(expectedItemRepository.findAllBySessionIdOrderByInventoryNumberAsc(
            eq(sessionId), any(Pageable.class)))
        .thenReturn(expectedItems);

    CursorPageResponse<InventoryExpectedItemDto> firstPage =
        inventoryService.getExpectedItemsCursor(sessionId, null, 2);

    assertThat(firstPage.items()).hasSize(2);
    assertThat(firstPage.hasMore()).isTrue();
    assertThat(firstPage.nextCursor()).isNotNull();

    // Decode cursor to verify it matches last item's inventory number
    String decodedInv =
        new String(Base64.getDecoder().decode(firstPage.nextCursor()), StandardCharsets.UTF_8);
    assertThat(decodedInv).isEqualTo("INV-002");
  }
}
