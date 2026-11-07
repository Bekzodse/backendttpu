package uz.ttpu.inventory.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import uz.ttpu.inventory.inventory.api.CursorPageResponse;
import uz.ttpu.inventory.inventory.api.InventoryExpectedItemDto;
import uz.ttpu.inventory.inventory.domain.InventoryExpectedItem;
import uz.ttpu.inventory.inventory.domain.InventoryLineStatus;
import uz.ttpu.inventory.inventory.domain.InventorySession;
import uz.ttpu.inventory.inventory.domain.InventorySessionStatus;
import uz.ttpu.inventory.inventory.repository.InventoryExpectedItemRepository;
import uz.ttpu.inventory.inventory.repository.InventorySessionRepository;
import uz.ttpu.inventory.inventory.service.InventoryMapper;
import uz.ttpu.inventory.inventory.service.InventoryService;

class CursorPaginationTest {

  private final List<InventoryExpectedItem> storedItems = new ArrayList<>();
  private InventoryExpectedItemRepository expectedItemRepository;
  private InventorySessionRepository sessionRepository;
  private InventoryService inventoryService;
  private final UUID sessionId = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @BeforeEach
  void setUp() {
    storedItems.clear();
    expectedItemRepository = mock(InventoryExpectedItemRepository.class);
    sessionRepository = mock(InventorySessionRepository.class);

    InventorySession session =
        InventorySession.builder()
            .sessionCode("INV-2026-S1")
            .status(InventorySessionStatus.IN_PROGRESS)
            .build();
    session.setId(sessionId);

    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

    // Populate 10 expected items in deterministic order by inventoryNumber
    for (int i = 1; i <= 10; i++) {
      UUID id = UUID.fromString(String.format("00000000-0000-0000-0000-%012d", i));
      InventoryExpectedItem item =
          InventoryExpectedItem.builder()
              .session(session)
              .roomId(UUID.randomUUID())
              .itemId(UUID.randomUUID())
              .inventoryNumber(String.format("INV-%04d", i))
              .qrCode(String.format("QR-%04d", i))
              .itemName(String.format("Item #%d", i))
              .lineStatus(InventoryLineStatus.EXPECTED)
              .build();
      item.setId(id);
      item.setCreatedAt(Instant.now());
      storedItems.add(item);
    }

    storedItems.sort(Comparator.comparing(InventoryExpectedItem::getInventoryNumber));

    when(expectedItemRepository.findAllBySessionIdOrderByInventoryNumberAsc(
            eq(sessionId), any(Pageable.class)))
        .thenAnswer(
            inv -> {
              Pageable pageable = inv.getArgument(1);
              int limit = pageable.getPageSize();
              return storedItems.stream().limit(limit).toList();
            });

    when(expectedItemRepository
            .findAllBySessionIdAndInventoryNumberGreaterThanOrderByInventoryNumberAsc(
                eq(sessionId), any(String.class), any(Pageable.class)))
        .thenAnswer(
            inv -> {
              String afterInv = inv.getArgument(1);
              Pageable pageable = inv.getArgument(2);
              int limit = pageable.getPageSize();
              return storedItems.stream()
                  .filter(item -> item.getInventoryNumber().compareTo(afterInv) > 0)
                  .limit(limit)
                  .toList();
            });

    InventoryMapper inventoryMapper =
        org.mapstruct.factory.Mappers.getMapper(InventoryMapper.class);
    inventoryService =
        new InventoryService(
            sessionRepository, null, expectedItemRepository, null, null, inventoryMapper, null);
  }

  @Test
  @DisplayName(
      "Prompt 13 / Inventory: Cursor pagination fetches first page with limit=3, hasMore=true, and"
          + " nextCursor")
  void testCursorPaginationFirstPage() {
    CursorPageResponse<InventoryExpectedItemDto> firstPage =
        inventoryService.getExpectedItemsCursor(sessionId, null, 3);

    assertThat(firstPage).isNotNull();
    assertThat(firstPage.items()).hasSize(3);
    assertThat(firstPage.limit()).isEqualTo(3);
    assertThat(firstPage.hasMore()).isTrue();
    assertThat(firstPage.nextCursor()).isNotNull();

    String expectedCursor =
        Base64.getEncoder().encodeToString("INV-0003".getBytes(StandardCharsets.UTF_8));
    assertThat(firstPage.nextCursor()).isEqualTo(expectedCursor);
  }

  @Test
  @DisplayName("Prompt 13 / Inventory: Fetching next cursor page returns subsequent records")
  void testCursorPaginationSecondPage() {
    CursorPageResponse<InventoryExpectedItemDto> firstPage =
        inventoryService.getExpectedItemsCursor(sessionId, null, 3);

    CursorPageResponse<InventoryExpectedItemDto> secondPage =
        inventoryService.getExpectedItemsCursor(sessionId, firstPage.nextCursor(), 3);

    assertThat(secondPage).isNotNull();
    assertThat(secondPage.items()).hasSize(3);
    assertThat(secondPage.items().get(0).inventoryNumber()).isEqualTo("INV-0004");
    assertThat(secondPage.items().get(1).inventoryNumber()).isEqualTo("INV-0005");
    assertThat(secondPage.items().get(2).inventoryNumber()).isEqualTo("INV-0006");
    assertThat(secondPage.hasMore()).isTrue();
  }

  @Test
  @DisplayName(
      "Prompt 13 / Inventory: Last cursor page correctly indicates hasMore=false and"
          + " nextCursor=null")
  void testCursorPaginationLastPage() {
    // Request starting after item 8 with limit 5 (only items 9 and 10 remain)
    String cursor8 =
        Base64.getEncoder().encodeToString("INV-0008".getBytes(StandardCharsets.UTF_8));
    CursorPageResponse<InventoryExpectedItemDto> lastPage =
        inventoryService.getExpectedItemsCursor(sessionId, cursor8, 5);

    assertThat(lastPage).isNotNull();
    assertThat(lastPage.items()).hasSize(2);
    assertThat(lastPage.items().get(0).inventoryNumber()).isEqualTo("INV-0009");
    assertThat(lastPage.items().get(1).inventoryNumber()).isEqualTo("INV-0010");
    assertThat(lastPage.hasMore()).isFalse();
    assertThat(lastPage.nextCursor()).isNull();
  }
}
