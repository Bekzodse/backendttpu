package uz.ttpu.inventory.movement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.common.domain.RoomLockedForInventoryException;
import uz.ttpu.inventory.inventory.api.InventoryLockApi;
import uz.ttpu.inventory.inventory.api.RoomLockStatusDto;
import uz.ttpu.inventory.movement.api.MovementConfirmRequest;
import uz.ttpu.inventory.movement.api.MovementRecordDto;
import uz.ttpu.inventory.movement.api.TransferInitiateRequest;
import uz.ttpu.inventory.movement.domain.MovementEventType;
import uz.ttpu.inventory.movement.domain.MovementRecord;
import uz.ttpu.inventory.movement.domain.MovementType;
import uz.ttpu.inventory.movement.repository.MovementRepository;
import uz.ttpu.inventory.movement.service.MovementMapper;
import uz.ttpu.inventory.movement.service.MovementService;
import uz.ttpu.inventory.security.api.TotpVerificationApi;

class InventoryTransferLockTest {

  private MovementRepository movementRepository;
  private AssetLogisticsApi assetLogisticsApi;
  private TotpVerificationApi totpVerificationApi;
  private MovementMapper movementMapper;
  private InventoryLockApi inventoryLockApi;
  private MovementService movementService;

  private final UUID lockedRoomId = UUID.randomUUID();
  private final UUID unlockedRoomId = UUID.randomUUID();
  private final UUID itemId = UUID.randomUUID();
  private final UUID custodianId = UUID.randomUUID();
  private final UUID recipientId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    movementRepository = mock(MovementRepository.class);
    assetLogisticsApi = mock(AssetLogisticsApi.class);
    totpVerificationApi = mock(TotpVerificationApi.class);
    inventoryLockApi = mock(InventoryLockApi.class);
    movementMapper = Mappers.getMapper(MovementMapper.class);

    movementService =
        new MovementService(
            movementRepository,
            assetLogisticsApi,
            totpVerificationApi,
            movementMapper,
            inventoryLockApi);

    when(inventoryLockApi.isRoomLockedForInventory(lockedRoomId)).thenReturn(true);
    when(inventoryLockApi.findActiveSessionCodeForRoom(lockedRoomId))
        .thenReturn(Optional.of("INV-2026-001"));
    when(inventoryLockApi.getRoomLockStatus(lockedRoomId))
        .thenReturn(
            new RoomLockStatusDto(
                lockedRoomId, true, "INV-2026-001", UUID.randomUUID(), "RECTOR-ORD-42"));

    when(inventoryLockApi.isRoomLockedForInventory(unlockedRoomId)).thenReturn(false);
    when(inventoryLockApi.findActiveSessionCodeForRoom(unlockedRoomId))
        .thenReturn(Optional.empty());
    when(inventoryLockApi.getRoomLockStatus(unlockedRoomId))
        .thenReturn(new RoomLockStatusDto(unlockedRoomId, false, null, null, null));
  }

  private ItemDto createItemDto(UUID id, UUID roomId, UUID custodianId) {
    return new ItemDto(
        id,
        "1001",
        "SN12345",
        "QR12345678",
        "Laptop Dell",
        "Dell Latitude",
        UUID.randomUUID(),
        ItemState.FOYDALANISHDA,
        new BigDecimal("1500000.00"),
        new BigDecimal("1200000.00"),
        false,
        Instant.now(),
        null,
        null,
        UUID.randomUUID(),
        UUID.randomUUID(),
        roomId,
        UUID.randomUUID(),
        custodianId,
        Map.of(),
        "1C-REF-01",
        Instant.now(),
        Instant.now());
  }

  @Test
  @DisplayName(
      "1. Initiate transfer is strictly blocked if item's source room is locked for inventory")
  void initiateTransfer_blockedWhenSourceRoomLocked() {
    ItemDto item = createItemDto(itemId, lockedRoomId, custodianId);
    when(assetLogisticsApi.resolveItemByIdOrQr(itemId, null)).thenReturn(item);

    TransferInitiateRequest request =
        new TransferInitiateRequest(
            itemId,
            null,
            "DOC-TRANS-01",
            MovementType.TRANSFER_ROOM_TO_ROOM,
            null,
            null,
            unlockedRoomId,
            null,
            recipientId,
            custodianId,
            "Transfer attempt from locked room");

    assertThatThrownBy(() -> movementService.initiateTransfer(request))
        .isInstanceOf(RoomLockedForInventoryException.class)
        .hasMessageContaining(lockedRoomId.toString())
        .hasMessageContaining("INV-2026-001");
  }

  @Test
  @DisplayName(
      "2. Initiate transfer is strictly blocked if destination room is locked for inventory")
  void initiateTransfer_blockedWhenDestinationRoomLocked() {
    ItemDto item = createItemDto(itemId, unlockedRoomId, custodianId);
    when(assetLogisticsApi.resolveItemByIdOrQr(itemId, null)).thenReturn(item);

    TransferInitiateRequest request =
        new TransferInitiateRequest(
            itemId,
            null,
            "DOC-TRANS-02",
            MovementType.TRANSFER_ROOM_TO_ROOM,
            null,
            null,
            lockedRoomId,
            null,
            recipientId,
            custodianId,
            "Transfer attempt to locked room");

    assertThatThrownBy(() -> movementService.initiateTransfer(request))
        .isInstanceOf(RoomLockedForInventoryException.class)
        .hasMessageContaining(lockedRoomId.toString())
        .hasMessageContaining("INV-2026-001");
  }

  @Test
  @DisplayName("3. Initiate transfer succeeds when neither source nor destination room is locked")
  void initiateTransfer_succeedsWhenNoRoomsLocked() {
    ItemDto item = createItemDto(itemId, unlockedRoomId, custodianId);
    when(assetLogisticsApi.resolveItemByIdOrQr(itemId, null)).thenReturn(item);
    when(movementRepository.save(any(MovementRecord.class)))
        .thenAnswer(
            inv -> {
              MovementRecord rec = inv.getArgument(0);
              rec.setId(UUID.randomUUID());
              return rec;
            });

    TransferInitiateRequest request =
        new TransferInitiateRequest(
            itemId,
            null,
            "DOC-TRANS-03",
            MovementType.TRANSFER_ROOM_TO_ROOM,
            null,
            null,
            unlockedRoomId,
            null,
            recipientId,
            custodianId,
            "Legitimate transfer");

    MovementRecordDto result = movementService.initiateTransfer(request);

    assertThat(result).isNotNull();
    assertThat(result.movementType()).isEqualTo(MovementType.TRANSFER_ROOM_TO_ROOM);
    assertThat(result.eventType()).isEqualTo(MovementEventType.TRANSFER_INITIATED);
    verify(assetLogisticsApi).initiateTransfer(itemId, "DOC-TRANS-03");
  }

  @Test
  @DisplayName(
      "4. Confirm transfer is strictly blocked if initial from-room or to-room becomes locked")
  void confirmTransfer_blockedWhenRoomLocked() {
    UUID operationId = UUID.randomUUID();
    MovementRecord initial =
        MovementRecord.builder()
            .operationId(operationId)
            .documentNumber("DOC-TRANS-04")
            .movementType(MovementType.TRANSFER_ROOM_TO_ROOM)
            .eventType(MovementEventType.TRANSFER_INITIATED)
            .itemId(itemId)
            .fromRoomId(lockedRoomId) // Locked
            .toRoomId(unlockedRoomId)
            .toEmployeeId(recipientId)
            .recordedAt(Instant.now())
            .build();

    when(movementRepository.findAllByOperationIdOrderByRecordedAtAsc(operationId))
        .thenReturn(List.of(initial));
    when(totpVerificationApi.verifyRecipientTotp(recipientId, "123456")).thenReturn(true);

    MovementConfirmRequest confirmRequest = new MovementConfirmRequest(recipientId, "123456", "OK");

    assertThatThrownBy(() -> movementService.confirmTransfer(operationId, confirmRequest))
        .isInstanceOf(RoomLockedForInventoryException.class)
        .hasMessageContaining("INV-2026-001");
  }

  @Test
  @DisplayName("5. Visual lock status query returns correct flags and rector order metadata")
  void getRoomInventoryLockStatus_returnsAccurateStatus() {
    RoomLockStatusDto lockedStatus = movementService.getRoomInventoryLockStatus(lockedRoomId);
    assertThat(lockedStatus.locked()).isTrue();
    assertThat(lockedStatus.sessionCode()).isEqualTo("INV-2026-001");
    assertThat(lockedStatus.rectorOrderNumber()).isEqualTo("RECTOR-ORD-42");

    RoomLockStatusDto unlockedStatus = movementService.getRoomInventoryLockStatus(unlockedRoomId);
    assertThat(unlockedStatus.locked()).isFalse();
    assertThat(unlockedStatus.sessionCode()).isNull();
  }
}
