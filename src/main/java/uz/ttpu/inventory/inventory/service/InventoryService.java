package uz.ttpu.inventory.inventory.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.inventory.api.CreateInventorySessionRequest;
import uz.ttpu.inventory.inventory.api.CursorPageResponse;
import uz.ttpu.inventory.inventory.api.InventoryExpectedItemDto;
import uz.ttpu.inventory.inventory.api.InventoryProgressDashboardDto;
import uz.ttpu.inventory.inventory.api.InventoryReconciliationReportDto;
import uz.ttpu.inventory.inventory.api.InventorySessionDto;
import uz.ttpu.inventory.inventory.api.OfflineScanActionRequest;
import uz.ttpu.inventory.inventory.api.OfflineScanActionResultDto;
import uz.ttpu.inventory.inventory.api.OfflineSessionPackageDto;
import uz.ttpu.inventory.inventory.api.OfflineSyncBatchRequest;
import uz.ttpu.inventory.inventory.api.OfflineSyncBatchResponseDto;
import uz.ttpu.inventory.inventory.api.ReconciledItemDto;
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

@Service
@RequiredArgsConstructor
public class InventoryService {

  public static final String TOPIC_SESSION_PROGRESS_PREFIX = "/topic/inventory/sessions/";
  public static final String TOPIC_SESSION_PROGRESS_SUFFIX = "/progress";
  public static final String TOPIC_GLOBAL_DASHBOARD = "/topic/inventory/dashboard";

  private final InventorySessionRepository sessionRepository;
  private final InventorySessionRoomRepository sessionRoomRepository;
  private final InventoryExpectedItemRepository expectedItemRepository;
  private final InventoryScanActionRepository scanActionRepository;
  private final AssetLogisticsApi assetLogisticsApi;
  private final InventoryMapper inventoryMapper;
  private final SimpMessagingTemplate messagingTemplate;

  @Transactional(readOnly = true)
  public List<InventorySessionDto> listSessions() {
    return sessionRepository.findAll().stream().map(inventoryMapper::toDto).toList();
  }

  @Transactional(readOnly = true)
  public InventorySessionDto getSession(UUID sessionId) {
    return inventoryMapper.toDto(findSessionOrThrow(sessionId));
  }

  @Transactional
  public InventorySessionDto createSession(CreateInventorySessionRequest request) {
    List<UUID> uniqueRooms = new ArrayList<>(new LinkedHashSet<>(request.roomIds()));
    List<UUID> uniqueMembers = new ArrayList<>(new LinkedHashSet<>(request.commissionMemberIds()));

    InventorySession session =
        InventorySession.builder()
            .sessionCode(request.sessionCode())
            .rectorOrderNumber(request.rectorOrderNumber())
            .rectorOrderDate(request.rectorOrderDate())
            .sessionDate(request.sessionDate())
            .status(InventorySessionStatus.DRAFT)
            .chairmanEmployeeId(request.chairmanEmployeeId())
            .commissionMemberIds(uniqueMembers)
            .roomIds(uniqueRooms)
            .notes(request.notes())
            .packageVersion(1L)
            .build();

    InventorySession saved = sessionRepository.save(session);
    if (request.generateExpectedListImmediately()) {
      generateExpectedListForSession(saved.getId());
      saved = findSessionOrThrow(saved.getId());
    }
    return inventoryMapper.toDto(saved);
  }

  @Transactional
  public List<RoomExpectedItemsDto> generateExpectedListForSession(UUID sessionId) {
    InventorySession session = findSessionOrThrow(sessionId);
    if (session.getStatus() == InventorySessionStatus.COMPLETED
        || session.getStatus() == InventorySessionStatus.CANCELLED) {
      throw new IllegalStateException(
          "Cannot regenerate expected list for session in state " + session.getStatus());
    }

    session.getSessionRooms().clear();
    session.getExpectedItems().clear();
    sessionRepository.saveAndFlush(session);

    List<UUID> roomIds = session.getRoomIds();
    List<ItemDto> currentItems = assetLogisticsApi.findExpectedItemsForRooms(roomIds);
    Map<UUID, List<ItemDto>> itemsByRoom =
        currentItems.stream()
            .filter(i -> i.roomId() != null)
            .collect(Collectors.groupingBy(ItemDto::roomId));

    Instant now = Instant.now();
    for (UUID roomId : roomIds) {
      List<ItemDto> roomItems = itemsByRoom.getOrDefault(roomId, List.of());
      int expectedCount = roomItems.size();
      boolean initiallyCompleted = expectedCount == 0;

      InventorySessionRoom sessionRoom =
          InventorySessionRoom.builder()
              .roomId(roomId)
              .expectedCount(expectedCount)
              .foundCount(0)
              .notFoundCount(expectedCount)
              .completed(initiallyCompleted)
              .completedAt(initiallyCompleted ? now : null)
              .build();
      session.addSessionRoom(sessionRoom);

      for (ItemDto item : roomItems) {
        BigDecimal bookVal =
            item.residualValue() != null
                ? item.residualValue()
                : (item.purchasePrice() != null ? item.purchasePrice() : BigDecimal.ZERO);
        InventoryExpectedItem expectedItem =
            InventoryExpectedItem.builder()
                .roomId(roomId)
                .itemId(item.id())
                .inventoryNumber(item.inventoryNumber())
                .qrCode(item.qrCode())
                .itemName(item.name())
                .custodianEmployeeId(item.custodianEmployeeId())
                .bookValue(bookVal)
                .lineStatus(InventoryLineStatus.EXPECTED)
                .build();
        session.addExpectedItem(expectedItem);
      }
    }

    session.setStatus(InventorySessionStatus.IN_PROGRESS);
    if (session.getStartedAt() == null) {
      session.setStartedAt(now);
    }
    session.incrementPackageVersion();
    sessionRepository.saveAndFlush(session);

    pushProgressDashboard(sessionId);
    return getExpectedListsByRoom(sessionId);
  }

  @Transactional(readOnly = true)
  public OfflineSessionPackageDto getOfflineSessionPackage(UUID sessionId) {
    InventorySession session = findSessionOrThrow(sessionId);
    List<RoomExpectedItemsDto> rooms = getExpectedListsByRoom(sessionId);
    List<InventoryExpectedItemDto> allExpected =
        expectedItemRepository.findAllBySessionIdOrderByInventoryNumberAsc(sessionId).stream()
            .map(inventoryMapper::toExpectedItemDto)
            .toList();

    return new OfflineSessionPackageDto(
        session.getId(),
        session.getSessionCode(),
        session.getRectorOrderNumber(),
        session.getRectorOrderDate(),
        session.getSessionDate(),
        session.getStatus(),
        session.getPackageVersion(),
        Instant.now(),
        rooms,
        allExpected);
  }

  @Transactional
  public OfflineSyncBatchResponseDto processOfflineBatch(OfflineSyncBatchRequest request) {
    if (scanActionRepository == null) {
      throw new IllegalStateException(
          "InventoryScanActionRepository is required for offline batch sync");
    }

    List<OfflineScanActionResultDto> results = new ArrayList<>();
    int appliedCount = 0;
    int duplicateIgnoredCount = 0;
    UUID primarySessionId = request.sessionId();

    for (OfflineScanActionRequest action : request.actions()) {
      UUID targetSessionId = action.sessionId() != null ? action.sessionId() : primarySessionId;
      if (targetSessionId == null) {
        throw new IllegalArgumentException(
            "sessionId must be provided on batch or individual action");
      }
      if (primarySessionId == null) {
        primarySessionId = targetSessionId;
      }

      Optional<InventoryScanAction> existingAction =
          scanActionRepository.findByClientActionId(action.clientActionId());
      if (existingAction.isPresent()) {
        InventoryScanAction dup = existingAction.get();
        duplicateIgnoredCount++;
        results.add(
            new OfflineScanActionResultDto(
                dup.getClientActionId(),
                dup.getId(),
                dup.getSession().getId(),
                dup.getItemId(),
                dup.getQrCode(),
                dup.getInventoryNumber(),
                dup.getExpectedRoomId(),
                dup.getScannedRoomId(),
                dup.getReconciliationCategory(),
                dup.getReconciliationCategory().getLabel(),
                true,
                dup.getScannedAt()));
        continue;
      }

      InventorySession session = findSessionOrThrow(targetSessionId);
      Instant scannedAt = action.scannedAt() != null ? action.scannedAt() : Instant.now();

      Optional<InventoryExpectedItem> expectedOpt =
          findExpectedItemOptional(
              targetSessionId, action.itemId(), action.qrCode(), action.inventoryNumber());

      ReconciliationCategory category;
      UUID resolvedItemId = action.itemId();
      String resolvedQr = action.qrCode();
      String resolvedInvNum = action.inventoryNumber();
      String resolvedName = action.itemName();
      UUID expectedRoomId = null;

      if (expectedOpt.isPresent()) {
        InventoryExpectedItem expectedItem = expectedOpt.get();
        expectedRoomId = expectedItem.getRoomId();
        resolvedItemId = expectedItem.getItemId();
        resolvedQr = expectedItem.getQrCode();
        resolvedInvNum = expectedItem.getInventoryNumber();
        resolvedName = expectedItem.getItemName();

        expectedItem.setScannedRoomId(action.scannedRoomId());
        expectedItem.setScannedByEmployeeId(action.scannedByEmployeeId());
        expectedItem.setScannedAt(scannedAt);
        expectedItem.setNotes(action.notes());

        if (expectedItem.getRoomId().equals(action.scannedRoomId())) {
          expectedItem.setLineStatus(InventoryLineStatus.FOUND);
          category = ReconciliationCategory.JOYIDA;
        } else {
          expectedItem.setLineStatus(InventoryLineStatus.MISPLACED_FOUND);
          category = ReconciliationCategory.BOSHQA_XONADA_TOPILDI;
        }

        expectedItemRepository.saveAndFlush(expectedItem);
        recalculateRoomProgress(session, expectedItem.getRoomId(), false);
      } else {
        category = ReconciliationCategory.ORTIQCHA;
        Optional<ItemDto> catalogItem =
            assetLogisticsApi.findItemByIdOrQrOptional(action.itemId(), action.qrCode());
        if (catalogItem.isPresent()) {
          ItemDto item = catalogItem.get();
          resolvedItemId = item.id();
          resolvedQr = item.qrCode();
          resolvedInvNum = item.inventoryNumber();
          resolvedName = item.name();
          expectedRoomId = item.roomId();
        }
      }

      InventoryScanAction scanRecord =
          InventoryScanAction.builder()
              .clientActionId(action.clientActionId())
              .session(session)
              .scannedRoomId(action.scannedRoomId())
              .scannedByEmployeeId(action.scannedByEmployeeId())
              .expectedRoomId(expectedRoomId)
              .itemId(resolvedItemId)
              .qrCode(resolvedQr)
              .inventoryNumber(resolvedInvNum)
              .itemName(resolvedName)
              .scannedAt(scannedAt)
              .reconciliationCategory(category)
              .notes(action.notes())
              .build();

      InventoryScanAction savedScan = scanActionRepository.saveAndFlush(scanRecord);
      appliedCount++;

      results.add(
          new OfflineScanActionResultDto(
              savedScan.getClientActionId(),
              savedScan.getId(),
              session.getId(),
              savedScan.getItemId(),
              savedScan.getQrCode(),
              savedScan.getInventoryNumber(),
              savedScan.getExpectedRoomId(),
              savedScan.getScannedRoomId(),
              savedScan.getReconciliationCategory(),
              savedScan.getReconciliationCategory().getLabel(),
              false,
              savedScan.getScannedAt()));
    }

    long version = 1L;
    if (primarySessionId != null) {
      InventorySession session = findSessionOrThrow(primarySessionId);
      if (appliedCount > 0) {
        session.incrementPackageVersion();
        sessionRepository.saveAndFlush(session);
        pushProgressDashboard(primarySessionId);
      }
      version = session.getPackageVersion();
    }

    return new OfflineSyncBatchResponseDto(
        primarySessionId,
        version,
        request.actions().size(),
        appliedCount,
        duplicateIgnoredCount,
        results);
  }

  @Transactional
  public InventoryReconciliationReportDto reconcileSession(UUID sessionId) {
    InventorySession session = findSessionOrThrow(sessionId);
    List<InventoryExpectedItem> expectedItems =
        expectedItemRepository.findAllBySessionIdOrderByInventoryNumberAsc(sessionId);

    List<ReconciledItemDto> joyidaList = new ArrayList<>();
    List<ReconciledItemDto> boshqaXonadaList = new ArrayList<>();
    List<ReconciledItemDto> topilmadiList = new ArrayList<>();
    List<ReconciledItemDto> ortiqchaList = new ArrayList<>();

    for (InventoryExpectedItem expected : expectedItems) {
      if (expected.getLineStatus() == InventoryLineStatus.FOUND) {
        assetLogisticsApi.markItemFoundDuringInventory(
            expected.getItemId(), expected.getScannedRoomId(), session.getSessionCode());
        joyidaList.add(
            new ReconciledItemDto(
                expected.getItemId(),
                expected.getInventoryNumber(),
                expected.getQrCode(),
                expected.getItemName(),
                expected.getRoomId(),
                expected.getScannedRoomId(),
                expected.getScannedByEmployeeId(),
                expected.getScannedAt(),
                ReconciliationCategory.JOYIDA,
                ReconciliationCategory.JOYIDA.getLabel(),
                false));
      } else if (expected.getLineStatus() == InventoryLineStatus.MISPLACED_FOUND) {
        assetLogisticsApi.markItemFoundDuringInventory(
            expected.getItemId(), expected.getScannedRoomId(), session.getSessionCode());
        boshqaXonadaList.add(
            new ReconciledItemDto(
                expected.getItemId(),
                expected.getInventoryNumber(),
                expected.getQrCode(),
                expected.getItemName(),
                expected.getRoomId(),
                expected.getScannedRoomId(),
                expected.getScannedByEmployeeId(),
                expected.getScannedAt(),
                ReconciliationCategory.BOSHQA_XONADA_TOPILDI,
                ReconciliationCategory.BOSHQA_XONADA_TOPILDI.getLabel(),
                false));
      } else {
        if (expected.getLineStatus() == InventoryLineStatus.EXPECTED) {
          expected.setLineStatus(InventoryLineStatus.MISSING);
          expectedItemRepository.save(expected);
        }
        assetLogisticsApi.markItemMissingDuringInventory(
            expected.getItemId(), session.getSessionCode());
        topilmadiList.add(
            new ReconciledItemDto(
                expected.getItemId(),
                expected.getInventoryNumber(),
                expected.getQrCode(),
                expected.getItemName(),
                expected.getRoomId(),
                null,
                null,
                null,
                ReconciliationCategory.TOPILMADI,
                ReconciliationCategory.TOPILMADI.getLabel(),
                true));
      }
    }
    expectedItemRepository.flush();

    if (scanActionRepository != null) {
      List<InventoryScanAction> surplusActions =
          scanActionRepository.findAllBySessionIdAndReconciliationCategoryOrderByScannedAtAsc(
              sessionId, ReconciliationCategory.ORTIQCHA);
      Set<String> seenSurplusKeys = new LinkedHashSet<>();
      for (InventoryScanAction surplus : surplusActions) {
        String dedupKey =
            surplus.getItemId() != null
                ? "ID:" + surplus.getItemId()
                : (surplus.getQrCode() != null && !surplus.getQrCode().isBlank()
                    ? "QR:" + surplus.getQrCode().toUpperCase()
                    : "ACT:" + surplus.getId());
        if (seenSurplusKeys.add(dedupKey)) {
          ortiqchaList.add(
              new ReconciledItemDto(
                  surplus.getItemId(),
                  surplus.getInventoryNumber(),
                  surplus.getQrCode(),
                  surplus.getItemName(),
                  surplus.getExpectedRoomId(),
                  surplus.getScannedRoomId(),
                  surplus.getScannedByEmployeeId(),
                  surplus.getScannedAt(),
                  ReconciliationCategory.ORTIQCHA,
                  ReconciliationCategory.ORTIQCHA.getLabel(),
                  false));
        }
      }
    }

    List<InventorySessionRoom> sessionRooms =
        sessionRoomRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId);
    for (InventorySessionRoom room : sessionRooms) {
      recalculateRoomProgress(session, room.getRoomId(), true);
    }

    Instant now = Instant.now();
    session.setStatus(InventorySessionStatus.COMPLETED);
    if (session.getCompletedAt() == null) {
      session.setCompletedAt(now);
    }
    session.incrementPackageVersion();
    sessionRepository.saveAndFlush(session);
    pushProgressDashboard(sessionId);

    return new InventoryReconciliationReportDto(
        session.getId(),
        session.getSessionCode(),
        session.getStatus(),
        session.getPackageVersion(),
        joyidaList.size(),
        boshqaXonadaList.size(),
        topilmadiList.size(),
        ortiqchaList.size(),
        joyidaList,
        boshqaXonadaList,
        topilmadiList,
        ortiqchaList,
        now);
  }

  @Transactional(readOnly = true)
  public List<RoomExpectedItemsDto> getExpectedListsByRoom(UUID sessionId) {
    findSessionOrThrow(sessionId);
    List<InventorySessionRoom> rooms =
        sessionRoomRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId);
    List<InventoryExpectedItem> allExpected =
        expectedItemRepository.findAllBySessionIdOrderByInventoryNumberAsc(sessionId);
    Map<UUID, List<InventoryExpectedItemDto>> itemsByRoom =
        allExpected.stream()
            .map(inventoryMapper::toExpectedItemDto)
            .collect(Collectors.groupingBy(InventoryExpectedItemDto::roomId));

    return rooms.stream()
        .map(
            room ->
                new RoomExpectedItemsDto(
                    sessionId,
                    room.getRoomId(),
                    room.getExpectedCount(),
                    room.getFoundCount(),
                    room.getNotFoundCount(),
                    room.isCompleted(),
                    room.getCompletedAt(),
                    itemsByRoom.getOrDefault(room.getRoomId(), List.of())))
        .toList();
  }

  @Transactional(readOnly = true)
  public RoomExpectedItemsDto getRoomExpectedList(UUID sessionId, UUID roomId) {
    findSessionOrThrow(sessionId);
    InventorySessionRoom room =
        sessionRoomRepository
            .findBySessionIdAndRoomId(sessionId, roomId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Room " + roomId + " not part of session " + sessionId));
    List<InventoryExpectedItemDto> items =
        expectedItemRepository
            .findAllBySessionIdAndRoomIdOrderByInventoryNumberAsc(sessionId, roomId)
            .stream()
            .map(inventoryMapper::toExpectedItemDto)
            .toList();
    return new RoomExpectedItemsDto(
        sessionId,
        room.getRoomId(),
        room.getExpectedCount(),
        room.getFoundCount(),
        room.getNotFoundCount(),
        room.isCompleted(),
        room.getCompletedAt(),
        items);
  }

  @Transactional(readOnly = true)
  public CursorPageResponse<InventoryExpectedItemDto> getExpectedItemsCursor(
      UUID sessionId, String cursor, int limit) {
    findSessionOrThrow(sessionId);
    int effectiveLimit = Math.max(1, Math.min(limit > 0 ? limit : 20, 100));
    PageRequest pageRequest = PageRequest.of(0, effectiveLimit + 1);

    List<InventoryExpectedItem> batch;
    if (cursor != null && !cursor.isBlank()) {
      String decodedInventoryNumber =
          new String(Base64.getDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
      batch =
          expectedItemRepository
              .findAllBySessionIdAndInventoryNumberGreaterThanOrderByInventoryNumberAsc(
                  sessionId, decodedInventoryNumber, pageRequest);
    } else {
      batch =
          expectedItemRepository.findAllBySessionIdOrderByInventoryNumberAsc(
              sessionId, pageRequest);
    }

    boolean hasMore = batch.size() > effectiveLimit;
    List<InventoryExpectedItem> resultItems = hasMore ? batch.subList(0, effectiveLimit) : batch;

    String nextCursor = null;
    if (hasMore && !resultItems.isEmpty()) {
      String lastInv = resultItems.get(resultItems.size() - 1).getInventoryNumber();
      nextCursor = Base64.getEncoder().encodeToString(lastInv.getBytes(StandardCharsets.UTF_8));
    }

    List<InventoryExpectedItemDto> dtoList =
        resultItems.stream().map(inventoryMapper::toExpectedItemDto).toList();

    return new CursorPageResponse<>(dtoList, nextCursor, hasMore, effectiveLimit);
  }

  @Transactional
  public InventoryExpectedItemDto scanItem(UUID sessionId, ScanInventoryItemRequest request) {
    InventorySession session = findSessionOrThrow(sessionId);
    if (session.getStatus() != InventorySessionStatus.IN_PROGRESS) {
      throw new IllegalStateException(
          "Session must be IN_PROGRESS to scan items, but was " + session.getStatus());
    }

    InventoryExpectedItem expectedItem =
        resolveExpectedItem(sessionId, request.itemId(), request.qrCode());
    Instant now = Instant.now();

    expectedItem.setScannedRoomId(request.scannedRoomId());
    expectedItem.setScannedByEmployeeId(request.scannedByEmployeeId());
    expectedItem.setScannedAt(now);
    expectedItem.setNotes(request.notes());

    if (expectedItem.getRoomId().equals(request.scannedRoomId())) {
      expectedItem.setLineStatus(InventoryLineStatus.FOUND);
    } else {
      expectedItem.setLineStatus(InventoryLineStatus.MISPLACED_FOUND);
    }

    InventoryExpectedItem savedItem = expectedItemRepository.saveAndFlush(expectedItem);
    recalculateRoomProgress(session, expectedItem.getRoomId(), false);
    session.incrementPackageVersion();
    sessionRepository.saveAndFlush(session);
    pushProgressDashboard(sessionId);
    return inventoryMapper.toExpectedItemDto(savedItem);
  }

  @Transactional
  public RoomProgressDto completeRoom(UUID sessionId, UUID roomId) {
    InventorySession session = findSessionOrThrow(sessionId);
    if (session.getStatus() != InventorySessionStatus.IN_PROGRESS) {
      throw new IllegalStateException(
          "Session must be IN_PROGRESS to complete a room, but was " + session.getStatus());
    }

    List<InventoryExpectedItem> roomItems =
        expectedItemRepository.findAllBySessionIdAndRoomIdOrderByInventoryNumberAsc(
            sessionId, roomId);
    for (InventoryExpectedItem item : roomItems) {
      if (item.getLineStatus() == InventoryLineStatus.EXPECTED) {
        item.setLineStatus(InventoryLineStatus.MISSING);
        expectedItemRepository.save(item);
      }
    }
    expectedItemRepository.flush();

    InventorySessionRoom updatedRoom = recalculateRoomProgress(session, roomId, true);
    session.incrementPackageVersion();
    sessionRepository.saveAndFlush(session);
    pushProgressDashboard(sessionId);
    return toRoomProgressDto(updatedRoom);
  }

  @Transactional(readOnly = true)
  public InventoryProgressDashboardDto getProgressDashboard(UUID sessionId) {
    InventorySession session = findSessionOrThrow(sessionId);
    List<InventorySessionRoom> sessionRooms =
        sessionRoomRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId);

    List<RoomProgressDto> roomDtos = sessionRooms.stream().map(this::toRoomProgressDto).toList();

    int totalRooms = roomDtos.size();
    int completedRooms = (int) roomDtos.stream().filter(RoomProgressDto::completed).count();
    int totalExpected = roomDtos.stream().mapToInt(RoomProgressDto::expectedCount).sum();
    int totalFound = roomDtos.stream().mapToInt(RoomProgressDto::foundCount).sum();
    int totalNotFound = roomDtos.stream().mapToInt(RoomProgressDto::notFoundCount).sum();

    BigDecimal overallPct =
        computePercentage(
            totalFound, totalExpected, totalRooms > 0 && completedRooms == totalRooms);

    return new InventoryProgressDashboardDto(
        session.getId(),
        session.getSessionCode(),
        session.getRectorOrderNumber(),
        session.getStatus(),
        totalRooms,
        completedRooms,
        totalExpected,
        totalFound,
        totalNotFound,
        overallPct,
        roomDtos,
        Instant.now());
  }

  @Transactional(readOnly = true)
  public InventoryProgressDashboardDto pushProgressDashboard(UUID sessionId) {
    InventoryProgressDashboardDto dashboard = getProgressDashboard(sessionId);
    String sessionDestination =
        TOPIC_SESSION_PROGRESS_PREFIX + sessionId + TOPIC_SESSION_PROGRESS_SUFFIX;
    messagingTemplate.convertAndSend(sessionDestination, dashboard);
    messagingTemplate.convertAndSend(TOPIC_GLOBAL_DASHBOARD, dashboard);
    return dashboard;
  }

  private InventorySessionRoom recalculateRoomProgress(
      InventorySession session, UUID roomId, boolean forceMarkCompleted) {
    InventorySessionRoom room =
        sessionRoomRepository
            .findBySessionIdAndRoomId(session.getId(), roomId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Room " + roomId + " not part of session " + session.getId()));

    List<InventoryExpectedItem> items =
        expectedItemRepository.findAllBySessionIdAndRoomIdOrderByInventoryNumberAsc(
            session.getId(), roomId);

    int expectedCount = items.size();
    int foundCount =
        (int)
            items.stream()
                .filter(
                    i ->
                        i.getLineStatus() == InventoryLineStatus.FOUND
                            || i.getLineStatus() == InventoryLineStatus.MISPLACED_FOUND)
                .count();
    int notFoundCount = Math.max(0, expectedCount - foundCount);

    room.setExpectedCount(expectedCount);
    room.setFoundCount(foundCount);
    room.setNotFoundCount(notFoundCount);

    boolean allFound = expectedCount > 0 && foundCount == expectedCount;
    if (forceMarkCompleted || allFound || expectedCount == 0) {
      room.setCompleted(true);
      if (room.getCompletedAt() == null) {
        room.setCompletedAt(Instant.now());
      }
    }
    InventorySessionRoom savedRoom = sessionRoomRepository.saveAndFlush(room);

    List<InventorySessionRoom> allRooms =
        sessionRoomRepository.findAllBySessionIdOrderByCreatedAtAsc(session.getId());
    boolean allRoomsCompleted =
        !allRooms.isEmpty() && allRooms.stream().allMatch(InventorySessionRoom::isCompleted);
    if (allRoomsCompleted && session.getStatus() == InventorySessionStatus.IN_PROGRESS) {
      session.setStatus(InventorySessionStatus.COMPLETED);
      session.setCompletedAt(Instant.now());
      sessionRepository.saveAndFlush(session);
    }

    return savedRoom;
  }

  private RoomProgressDto toRoomProgressDto(InventorySessionRoom room) {
    BigDecimal pct =
        computePercentage(room.getFoundCount(), room.getExpectedCount(), room.isCompleted());
    return new RoomProgressDto(
        room.getRoomId(),
        room.getExpectedCount(),
        room.getFoundCount(),
        room.getNotFoundCount(),
        pct,
        room.isCompleted(),
        room.getCompletedAt());
  }

  private BigDecimal computePercentage(int found, int expected, boolean completedWhenEmpty) {
    if (expected <= 0) {
      return completedWhenEmpty ? new BigDecimal("100.00") : new BigDecimal("0.00");
    }
    return BigDecimal.valueOf(found)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(expected), 2, RoundingMode.HALF_UP);
  }

  private Optional<InventoryExpectedItem> findExpectedItemOptional(
      UUID sessionId, UUID itemId, String qrCode, String inventoryNumber) {
    if (itemId != null) {
      Optional<InventoryExpectedItem> byId =
          expectedItemRepository.findBySessionIdAndItemId(sessionId, itemId);
      if (byId.isPresent()) {
        return byId;
      }
    }
    if (qrCode != null && !qrCode.isBlank()) {
      Optional<InventoryExpectedItem> byQr =
          expectedItemRepository.findBySessionIdAndQrCodeIgnoreCase(sessionId, qrCode.trim());
      if (byQr.isPresent()) {
        return byQr;
      }
    }
    if (inventoryNumber != null && !inventoryNumber.isBlank()) {
      return expectedItemRepository.findBySessionIdAndInventoryNumberIgnoreCase(
          sessionId, inventoryNumber.trim());
    }
    return Optional.empty();
  }

  private InventoryExpectedItem resolveExpectedItem(UUID sessionId, UUID itemId, String qrCode) {
    if (itemId != null) {
      return expectedItemRepository
          .findBySessionIdAndItemId(sessionId, itemId)
          .orElseThrow(
              () ->
                  new ResourceNotFoundException(
                      "Expected item " + itemId + " not found in session " + sessionId));
    }
    if (qrCode != null && !qrCode.isBlank()) {
      return expectedItemRepository
          .findBySessionIdAndQrCodeIgnoreCase(sessionId, qrCode.trim())
          .orElseThrow(
              () ->
                  new ResourceNotFoundException(
                      "Expected item with QR " + qrCode + " not found in session " + sessionId));
    }
    throw new IllegalArgumentException(
        "Either itemId or qrCode must be provided when scanning an inventory item");
  }

  private InventorySession findSessionOrThrow(UUID sessionId) {
    return sessionRepository
        .findById(sessionId)
        .orElseThrow(() -> new ResourceNotFoundException("InventorySession", sessionId));
  }
}
