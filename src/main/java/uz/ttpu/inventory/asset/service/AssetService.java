package uz.ttpu.inventory.asset.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.AssetSearchFilterRequest;
import uz.ttpu.inventory.asset.api.AssetSearchResultDto;
import uz.ttpu.inventory.asset.api.CreateItemRequest;
import uz.ttpu.inventory.asset.api.DataWipeRecordDto;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.asset.api.ItemStateHistoryDto;
import uz.ttpu.inventory.asset.api.PilotItemUpsertRequest;
import uz.ttpu.inventory.asset.api.PublicItemCardDto;
import uz.ttpu.inventory.asset.api.RecordDataWipeRequest;
import uz.ttpu.inventory.asset.api.TransitionItemRequest;
import uz.ttpu.inventory.asset.domain.DataWipeRecord;
import uz.ttpu.inventory.asset.domain.IllegalStateTransitionException;
import uz.ttpu.inventory.asset.domain.Item;
import uz.ttpu.inventory.asset.domain.ItemEvent;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.asset.repository.DataWipeRecordRepository;
import uz.ttpu.inventory.asset.repository.ItemRepository;
import uz.ttpu.inventory.asset.repository.ItemStateHistoryRepository;
import uz.ttpu.inventory.common.domain.AuditActionType;
import uz.ttpu.inventory.common.domain.AuditableItemAction;
import uz.ttpu.inventory.common.domain.DataWipeRequiredException;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.receipt.api.ReceiptApprovedEvent;
import uz.ttpu.inventory.references.api.CategoryDto;
import uz.ttpu.inventory.references.api.ReferenceSyncApi;
import uz.ttpu.inventory.repair.api.ItemMovedToVendorRepairEvent;
import uz.ttpu.inventory.repair.api.ItemRepairCompletedEvent;
import uz.ttpu.inventory.repair.api.RepairReplacementRecommendedEvent;
import uz.ttpu.inventory.workflow.api.WorkflowCompletedEvent;

@Service
public class AssetService implements AssetLogisticsApi {

  private final ItemRepository itemRepository;
  private final ItemStateHistoryRepository itemStateHistoryRepository;
  private final ItemStateService itemStateService;
  private final QrIdService qrIdService;
  private final ItemMapper itemMapper;
  private final DataWipeRecordRepository dataWipeRecordRepository;
  private final ReferenceSyncApi referenceSyncApi;
  private final ApplicationEventPublisher eventPublisher;

  @org.springframework.beans.factory.annotation.Autowired
  public AssetService(
      ItemRepository itemRepository,
      ItemStateHistoryRepository itemStateHistoryRepository,
      ItemStateService itemStateService,
      QrIdService qrIdService,
      ItemMapper itemMapper,
      DataWipeRecordRepository dataWipeRecordRepository,
      ReferenceSyncApi referenceSyncApi,
      ApplicationEventPublisher eventPublisher) {
    this.itemRepository = itemRepository;
    this.itemStateHistoryRepository = itemStateHistoryRepository;
    this.itemStateService = itemStateService;
    this.qrIdService = qrIdService;
    this.itemMapper = itemMapper;
    this.dataWipeRecordRepository = dataWipeRecordRepository;
    this.referenceSyncApi = referenceSyncApi;
    this.eventPublisher = eventPublisher;
  }

  public AssetService(
      ItemRepository itemRepository,
      ItemStateHistoryRepository itemStateHistoryRepository,
      ItemStateService itemStateService,
      QrIdService qrIdService,
      ItemMapper itemMapper) {
    this(
        itemRepository,
        itemStateHistoryRepository,
        itemStateService,
        qrIdService,
        itemMapper,
        null,
        null,
        null);
  }

  @Value("${app.public-card.contact-phone:+998 71 246-70-82}")
  private String publicContactPhone = "+998 71 246-70-82";

  @Transactional(readOnly = true)
  @AuditableItemAction(AuditActionType.READ)
  public List<ItemDto> listItems(ItemState state, UUID departmentId, UUID buildingId) {
    List<Item> items;
    if (state != null) {
      items = itemRepository.findAllByState(state);
    } else if (departmentId != null) {
      items = itemRepository.findAllByDepartmentId(departmentId);
    } else if (buildingId != null) {
      items = itemRepository.findAllByBuildingId(buildingId);
    } else {
      items = itemRepository.findAll();
    }
    return items.stream().map(itemMapper::toDto).toList();
  }

  @Transactional(readOnly = true)
  @AuditableItemAction(AuditActionType.READ)
  public ItemDto getItem(UUID id) {
    return itemMapper.toDto(findItemOrThrow(id));
  }

  @Transactional(readOnly = true)
  @AuditableItemAction(AuditActionType.READ)
  public PublicItemCardDto getPublicItemCard(String qrId) {
    String normalizedQrId = qrId != null ? qrId.trim() : "";
    Item item =
        itemRepository
            .findByQrCodeIgnoreCase(normalizedQrId)
            .orElseGet(
                () ->
                    itemRepository
                        .findByQrCode(normalizedQrId)
                        .orElseThrow(
                            () ->
                                new ResourceNotFoundException(
                                    "Item not found with QR ID: " + qrId)));
    return new PublicItemCardDto(
        item.getQrCode(),
        item.getName(),
        item.getInventoryNumber(),
        PublicItemCardDto.DEFAULT_OWNERSHIP_LABEL,
        publicContactPhone);
  }

  @Transactional
  @AuditableItemAction(AuditActionType.CHANGE)
  public ItemDto createItem(CreateItemRequest request) {
    String qrCode =
        (request.qrCode() != null && !request.qrCode().isBlank())
            ? request.qrCode()
            : qrIdService.generateUniquePublicId();

    Item item =
        Item.builder()
            .inventoryNumber(request.inventoryNumber())
            .serialNumber(request.serialNumber())
            .qrCode(qrCode)
            .name(request.name())
            .description(request.description())
            .categoryId(request.categoryId())
            .purchasePrice(request.purchasePrice())
            .residualValue(request.residualValue())
            .commissionedAt(request.commissionedAt())
            .warrantyExpiresAt(request.warrantyExpiresAt())
            .expectedReturnAt(request.expectedReturnAt())
            .buildingId(request.buildingId())
            .floorId(request.floorId())
            .roomId(request.roomId())
            .departmentId(request.departmentId())
            .custodianEmployeeId(request.custodianEmployeeId())
            .customAttributes(
                request.customAttributes() != null
                    ? new HashMap<>(request.customAttributes())
                    : new HashMap<>())
            .oneCRefId(request.oneCRefId())
            .build();
    return itemMapper.toDto(itemRepository.save(item));
  }

  @Transactional
  @AuditableItemAction(AuditActionType.CHANGE)
  public ItemDto updateItem(UUID id, CreateItemRequest request) {
    Item item = findItemOrThrow(id);
    item.setInventoryNumber(request.inventoryNumber());
    item.setSerialNumber(request.serialNumber());
    if (request.qrCode() != null && !request.qrCode().isBlank()) {
      item.setQrCode(request.qrCode());
    }
    item.setName(request.name());
    item.setDescription(request.description());
    item.setCategoryId(request.categoryId());
    item.setPurchasePrice(request.purchasePrice());
    item.setResidualValue(request.residualValue());
    item.setCommissionedAt(request.commissionedAt());
    item.setWarrantyExpiresAt(request.warrantyExpiresAt());
    item.setExpectedReturnAt(request.expectedReturnAt());
    item.setBuildingId(request.buildingId());
    item.setFloorId(request.floorId());
    item.setRoomId(request.roomId());
    item.setDepartmentId(request.departmentId());
    item.setCustodianEmployeeId(request.custodianEmployeeId());
    item.setCustomAttributes(
        request.customAttributes() != null
            ? new HashMap<>(request.customAttributes())
            : new HashMap<>());
    item.setOneCRefId(request.oneCRefId());
    return itemMapper.toDto(itemRepository.save(item));
  }

  @Transactional
  @AuditableItemAction(AuditActionType.CHANGE)
  public ItemDto transitionItemState(UUID id, TransitionItemRequest request) {
    Item item = findItemOrThrow(id);
    if (request.event() == ItemEvent.APPROVE_WRITE_OFF) {
      validateDataWipeBeforeWriteOff(item);
    }
    Item updated = itemStateService.transition(item, request.event(), request.documentReference());
    return itemMapper.toDto(updated);
  }

  @Transactional(readOnly = true)
  @AuditableItemAction(AuditActionType.EXPORT)
  public ItemDto exportItemCard(UUID id) {
    return itemMapper.toDto(findItemOrThrow(id));
  }

  @EventListener
  @Transactional
  @AuditableItemAction(AuditActionType.CHANGE)
  public List<ItemDto> onReceiptApproved(ReceiptApprovedEvent event) {
    List<ItemDto> createdItems = new ArrayList<>();
    int lineSequence = 1;
    for (ReceiptApprovedEvent.ApprovedReceiptLineItem line : event.lines()) {
      int count = Math.max(1, line.quantity());
      for (int i = 1; i <= count; i++) {
        String qrCode = qrIdService.generateUniquePublicId();
        String invNumber =
            "TTPU-" + event.receiptNumber() + "-" + String.format("%03d", lineSequence++);
        String serial = count == 1 ? line.serialNumber() : line.serialNumber() + "-" + i;

        Item item =
            Item.builder()
                .inventoryNumber(invNumber)
                .serialNumber(serial)
                .qrCode(qrCode)
                .name(line.name())
                .categoryId(line.categoryId())
                .purchasePrice(line.priceWithoutVat())
                .residualValue(line.priceWithoutVat())
                .buildingId(event.warehouseBuildingId())
                .build();
        createdItems.add(itemMapper.toDto(itemRepository.save(item)));
      }
    }
    return createdItems;
  }

  @EventListener
  @Transactional
  @AuditableItemAction(AuditActionType.CHANGE)
  public ItemDto onItemMovedToVendorRepair(ItemMovedToVendorRepairEvent event) {
    Item item = findItemOrThrow(event.itemId());
    if (event.expectedReturnAt() != null) {
      item.setExpectedReturnAt(event.expectedReturnAt());
    }
    Item updated =
        itemStateService.transition(item, ItemEvent.SEND_TO_REPAIR, event.ticketNumber());
    evaluate50PercentRule(updated, event.ticketId(), event.ticketNumber(), event.estimatedCost());
    return itemMapper.toDto(updated);
  }

  @EventListener
  @Transactional
  @AuditableItemAction(AuditActionType.CHANGE)
  public ItemDto onItemRepairCompleted(ItemRepairCompletedEvent event) {
    Item item = findItemOrThrow(event.itemId());
    Item target = item;
    if (item.getState() == ItemState.TAMIRDA) {
      item.setExpectedReturnAt(null);
      target = itemStateService.transition(item, ItemEvent.COMPLETE_REPAIR, event.ticketNumber());
    }
    evaluate50PercentRule(target, event.ticketId(), event.ticketNumber(), event.actualCost());
    return itemMapper.toDto(target);
  }

  @EventListener
  @Transactional
  @AuditableItemAction(AuditActionType.CHANGE)
  public Optional<ItemDto> onWorkflowCompleted(WorkflowCompletedEvent event) {
    if (!event.isWriteOffDocument() || event.documentId() == null) {
      return Optional.empty();
    }
    return itemRepository
        .findById(event.documentId())
        .map(
            item -> {
              if (item.getState() == ItemState.HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN) {
                String docRef =
                    event.documentNumber() != null && !event.documentNumber().isBlank()
                        ? event.documentNumber()
                        : event.instanceId().toString();
                if (event.isApproved()) {
                  validateDataWipeBeforeWriteOff(item);
                  return itemMapper.toDto(
                      itemStateService.transition(item, ItemEvent.APPROVE_WRITE_OFF, docRef));
                } else if (event.isRejected()) {
                  return itemMapper.toDto(
                      itemStateService.transition(item, ItemEvent.REJECT_WRITE_OFF, docRef));
                }
              }
              return itemMapper.toDto(item);
            });
  }

  @Transactional(readOnly = true)
  public List<ItemStateHistoryDto> getItemHistory(UUID id) {
    findItemOrThrow(id);
    return itemStateHistoryRepository.findAllByItemIdOrderByTransitionedAtDesc(id).stream()
        .map(itemMapper::toHistoryDto)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public ItemDto resolveItemByIdOrQr(UUID itemId, String qrCode) {
    if (itemId != null) {
      return itemMapper.toDto(findItemOrThrow(itemId));
    }
    if (qrCode != null && !qrCode.isBlank()) {
      Item item =
          itemRepository
              .findByQrCodeIgnoreCase(qrCode.trim())
              .orElseThrow(
                  () -> new ResourceNotFoundException("Item not found with QR code: " + qrCode));
      return itemMapper.toDto(item);
    }
    throw new IllegalArgumentException("Either itemId or qrCode must be provided");
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ItemDto> findItemByIdOrQrOptional(UUID itemId, String qrCode) {
    if (itemId != null) {
      return itemRepository.findById(itemId).map(itemMapper::toDto);
    }
    if (qrCode != null && !qrCode.isBlank()) {
      return itemRepository.findByQrCodeIgnoreCase(qrCode.trim()).map(itemMapper::toDto);
    }
    return Optional.empty();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ItemDto> listAllItemsForReporting() {
    return itemRepository.findAll().stream().map(itemMapper::toDto).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ItemDto> findExpectedItemsForRooms(Collection<UUID> roomIds) {
    if (roomIds == null || roomIds.isEmpty()) {
      return List.of();
    }
    return itemRepository
        .findAllByRoomIdInAndStateNot(roomIds, ItemState.HISOBDAN_CHIQARILGAN)
        .stream()
        .map(itemMapper::toDto)
        .toList();
  }

  @Override
  @Transactional
  @AuditableItemAction(AuditActionType.CHANGE)
  public ItemDto markItemMissingDuringInventory(UUID itemId, String sessionCode) {
    Item item = findItemOrThrow(itemId);
    if (item.getState() == ItemState.QIDIRUVDA
        || !itemStateService.isTransitionAllowed(item.getState(), ItemEvent.MARK_MISSING)) {
      return itemMapper.toDto(item);
    }
    Item updated = itemStateService.transition(item, ItemEvent.MARK_MISSING, sessionCode);
    return itemMapper.toDto(updated);
  }

  @Override
  @Transactional
  @AuditableItemAction(AuditActionType.CHANGE)
  public ItemDto markItemFoundDuringInventory(UUID itemId, UUID scannedRoomId, String sessionCode) {
    Item item = findItemOrThrow(itemId);
    if (item.getState() == ItemState.QIDIRUVDA
        && itemStateService.isTransitionAllowed(item.getState(), ItemEvent.FOUND_IN_USE)) {
      if (scannedRoomId != null) {
        item.setRoomId(scannedRoomId);
      }
      Item updated = itemStateService.transition(item, ItemEvent.FOUND_IN_USE, sessionCode);
      return itemMapper.toDto(updated);
    }
    return itemMapper.toDto(item);
  }

  @Override
  @Transactional
  @AuditableItemAction(AuditActionType.CHANGE)
  public ItemDto applyOneCDepreciation(
      UUID itemId, String inventoryNumber, String oneCRefId, BigDecimal newResidualValue) {
    Item item;
    if (itemId != null) {
      item = findItemOrThrow(itemId);
    } else if (inventoryNumber != null && !inventoryNumber.isBlank()) {
      item =
          itemRepository
              .findByInventoryNumberIgnoreCase(inventoryNumber.trim())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Item not found with inventoryNumber: " + inventoryNumber));
    } else {
      throw new IllegalArgumentException(
          "Either itemId or inventoryNumber must be provided for 1C depreciation");
    }

    if (oneCRefId != null && !oneCRefId.isBlank()) {
      item.setOneCRefId(oneCRefId);
    }
    if (newResidualValue != null) {
      item.setResidualValue(newResidualValue);
    }
    return itemMapper.toDto(itemRepository.save(item));
  }

  @Override
  @Transactional
  @AuditableItemAction(AuditActionType.CHANGE)
  public ItemDto dispatchForIssue(UUID itemId, String documentNumber) {
    Item item = findItemOrThrow(itemId);
    if (item.getState() != ItemState.OMBORDA) {
      throw new IllegalStateTransitionException(
          "Issue requires item to be in OMBORDA state, but was " + item.getState());
    }
    Item updated = itemStateService.transition(item, ItemEvent.DISPATCH, documentNumber);
    return itemMapper.toDto(updated);
  }

  @Override
  @Transactional
  @AuditableItemAction(AuditActionType.CHANGE)
  public ItemDto confirmIssue(
      UUID itemId,
      UUID toBuildingId,
      UUID toFloorId,
      UUID toRoomId,
      UUID toDepartmentId,
      UUID toEmployeeId,
      String documentNumber) {
    Item item = findItemOrThrow(itemId);
    if (toBuildingId != null) {
      item.setBuildingId(toBuildingId);
    }
    if (toFloorId != null) {
      item.setFloorId(toFloorId);
    }
    if (toRoomId != null) {
      item.setRoomId(toRoomId);
    }
    if (toDepartmentId != null) {
      item.setDepartmentId(toDepartmentId);
    }
    if (toEmployeeId != null) {
      item.setCustodianEmployeeId(toEmployeeId);
    }
    Item updated = itemStateService.transition(item, ItemEvent.PUT_INTO_USE, documentNumber);
    return itemMapper.toDto(updated);
  }

  @Override
  @Transactional
  @AuditableItemAction(AuditActionType.CHANGE)
  public ItemDto rejectIssue(UUID itemId, String documentNumber) {
    Item item = findItemOrThrow(itemId);
    Item updated = itemStateService.transition(item, ItemEvent.RETURN_TO_WAREHOUSE, documentNumber);
    return itemMapper.toDto(updated);
  }

  @Override
  @Transactional
  @AuditableItemAction(AuditActionType.CHANGE)
  public ItemDto initiateTransfer(UUID itemId, String documentNumber) {
    Item item = findItemOrThrow(itemId);
    if (item.getState() != ItemState.FOYDALANISHDA) {
      throw new IllegalStateTransitionException(
          "Transfer requires item to be in FOYDALANISHDA state, but was " + item.getState());
    }
    Item updated = itemStateService.transition(item, ItemEvent.DISPATCH, documentNumber);
    return itemMapper.toDto(updated);
  }

  @Override
  @Transactional
  @AuditableItemAction(AuditActionType.CHANGE)
  public ItemDto confirmTransfer(
      UUID itemId,
      UUID toBuildingId,
      UUID toFloorId,
      UUID toRoomId,
      UUID toDepartmentId,
      UUID toEmployeeId,
      String documentNumber) {
    return confirmIssue(
        itemId, toBuildingId, toFloorId, toRoomId, toDepartmentId, toEmployeeId, documentNumber);
  }

  @Transactional(readOnly = true)
  @AuditableItemAction(AuditActionType.READ)
  public AssetSearchResultDto searchItems(AssetSearchFilterRequest filter, int page, int size) {
    long startNanos = System.nanoTime();
    int safePage = Math.max(0, page);
    int safeSize = Math.min(200, Math.max(1, size));
    Pageable pageable =
        PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "inventoryNumber"));
    Specification<Item> spec = ItemFuzzySearchSpecification.withFilter(filter);
    Page<Item> resultPage = itemRepository.findAll(spec, pageable);
    List<ItemDto> items = resultPage.getContent().stream().map(itemMapper::toDto).toList();
    long elapsedMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
    return new AssetSearchResultDto(
        items,
        resultPage.getTotalElements(),
        resultPage.getTotalPages(),
        safePage,
        safeSize,
        elapsedMs);
  }

  @Override
  @Transactional
  @AuditableItemAction(AuditActionType.CHANGE)
  public ItemDto rejectTransfer(UUID itemId, String documentNumber) {
    Item item = findItemOrThrow(itemId);
    Item updated = itemStateService.transition(item, ItemEvent.PUT_INTO_USE, documentNumber);
    return itemMapper.toDto(updated);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByInventoryNumber(String inventoryNumber) {
    if (inventoryNumber == null || inventoryNumber.isBlank()) {
      return false;
    }
    return itemRepository.existsByInventoryNumberIgnoreCase(inventoryNumber.trim());
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsBySerialNumber(String serialNumber) {
    if (serialNumber == null || serialNumber.isBlank()) {
      return false;
    }
    return itemRepository.existsBySerialNumberIgnoreCase(serialNumber.trim());
  }

  @Override
  @Transactional
  @AuditableItemAction(AuditActionType.CHANGE)
  public ItemDto upsertPilotMigratedItem(PilotItemUpsertRequest request) {
    Item item =
        itemRepository
            .findByInventoryNumberIgnoreCase(request.inventoryNumber().trim())
            .orElseGet(
                () ->
                    Item.builder()
                        .inventoryNumber(request.inventoryNumber().trim())
                        .qrCode(qrIdService.generateUniquePublicId())
                        .build());

    item.setName(request.name());
    item.setSerialNumber(request.serialNumber());
    item.setModel(request.model());
    item.setCategoryId(request.categoryId());
    item.setBuildingId(request.buildingId());
    item.setFloorId(request.floorId());
    item.setRoomId(request.roomId());
    item.setRoom(request.roomLabel());
    item.setDepartmentId(request.departmentId());
    item.setCustodianEmployeeId(request.custodianEmployeeId());
    item.setResponsiblePerson(request.responsiblePersonLabel());
    item.setPurchasePrice(
        request.purchasePrice() != null ? request.purchasePrice() : BigDecimal.ZERO);
    item.setResidualValue(
        request.residualValue() != null ? request.residualValue() : BigDecimal.ZERO);
    item.setCommissionedAt(request.commissionedAt());
    item.setOneCRefId(request.oneCRefId());
    if (request.customAttributes() != null) {
      item.setCustomAttributes(new HashMap<>(request.customAttributes()));
    }

    Item saved = itemRepository.save(item);
    if (saved.getState() == ItemState.OMBORDA && saved.getRoomId() != null) {
      Item dispatched =
          itemStateService.transition(saved, ItemEvent.DISPATCH, "PILOT-1C-MIGRATION");
      saved = itemStateService.transition(dispatched, ItemEvent.PUT_INTO_USE, "PILOT-1C-MIGRATION");
    }
    return itemMapper.toDto(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ItemDto> findItemsByResponsibleEmployee(UUID employeeId) {
    if (employeeId == null) {
      return List.of();
    }
    return itemRepository
        .findAllByCustodianEmployeeIdAndStateNot(employeeId, ItemState.HISOBDAN_CHIQARILGAN)
        .stream()
        .map(itemMapper::toDto)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countAssignedItems(UUID employeeId) {
    if (employeeId == null) {
      return 0L;
    }
    return itemRepository.countByCustodianEmployeeIdAndStateNot(
        employeeId, ItemState.HISOBDAN_CHIQARILGAN);
  }

  @Override
  @Transactional
  public void setReplacementRecommended(UUID itemId, boolean recommended) {
    Item item = findItemOrThrow(itemId);
    item.setReplacementRecommended(recommended);
    itemRepository.save(item);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isDataWipeRequired(UUID itemId) {
    if (itemId == null) {
      return false;
    }
    return itemRepository.findById(itemId).map(this::isDataWipeRequiredItem).orElse(false);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasCertifiedDataWipe(UUID itemId) {
    if (itemId == null) {
      return false;
    }
    return dataWipeRecordRepository.existsByItemId(itemId);
  }

  @Override
  @Transactional
  public DataWipeRecordDto recordDataWipe(UUID itemId, RecordDataWipeRequest request) {
    findItemOrThrow(itemId);
    Instant wipedAt = request.wipedAt() != null ? request.wipedAt() : Instant.now();
    DataWipeRecord record =
        DataWipeRecord.builder()
            .itemId(itemId)
            .performedByName(request.performedByName())
            .performedByEmployeeId(request.performedByEmployeeId())
            .wipeMethod(request.wipeMethod())
            .certificateNumber(request.certificateNumber())
            .notes(request.notes())
            .wipedAt(wipedAt)
            .build();
    DataWipeRecord saved = dataWipeRecordRepository.save(record);
    return toDataWipeDto(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DataWipeRecordDto> getLatestDataWipe(UUID itemId) {
    if (itemId == null) {
      return Optional.empty();
    }
    return dataWipeRecordRepository
        .findTopByItemIdOrderByWipedAtDesc(itemId)
        .map(this::toDataWipeDto);
  }

  private void validateDataWipeBeforeWriteOff(Item item) {
    if (isDataWipeRequiredItem(item) && !hasCertifiedDataWipe(item.getId())) {
      String catName = getCategoryNameSafe(item.getCategoryId());
      throw new DataWipeRequiredException(item.getId(), item.getInventoryNumber(), catName);
    }
  }

  private String getCategoryNameSafe(UUID categoryId) {
    if (categoryId == null || referenceSyncApi == null) {
      return "IT Device";
    }
    return referenceSyncApi.findCategoryById(categoryId).map(CategoryDto::name).orElse("IT Device");
  }

  private boolean isDataWipeRequiredItem(Item item) {
    if (item == null) {
      return false;
    }
    if (item.getCustomAttributes() != null) {
      Object req = item.getCustomAttributes().get("dataWipeRequired");
      if (Boolean.TRUE.equals(req) || "true".equalsIgnoreCase(String.valueOf(req))) {
        return true;
      }
    }
    String text =
        (safeStr(item.getName())
                + " "
                + safeStr(item.getModel())
                + " "
                + safeStr(item.getDescription()))
            .toLowerCase(Locale.ROOT);
    if (text.contains("computer")
        || text.contains("kompyuter")
        || text.contains("laptop")
        || text.contains("noutbuk")
        || text.contains("phone")
        || text.contains("telefon")
        || text.contains("server")
        || text.contains("printer")
        || text.contains("print")
        || text.contains("mfp")) {
      return true;
    }
    if (item.getCategoryId() != null && referenceSyncApi != null) {
      Optional<CategoryDto> catOpt = referenceSyncApi.findCategoryById(item.getCategoryId());
      if (catOpt.isPresent()) {
        String catCode = safeStr(catOpt.get().code()).toLowerCase(Locale.ROOT);
        String catName = safeStr(catOpt.get().name()).toLowerCase(Locale.ROOT);
        if (catCode.contains("comp")
            || catCode.contains("laptop")
            || catCode.contains("server")
            || catCode.contains("phone")
            || catCode.contains("print")
            || catName.contains("computer")
            || catName.contains("kompyuter")
            || catName.contains("laptop")
            || catName.contains("noutbuk")
            || catName.contains("server")
            || catName.contains("phone")
            || catName.contains("telefon")
            || catName.contains("printer")) {
          return true;
        }
      }
    }
    return false;
  }

  private String safeStr(String s) {
    return s != null ? s : "";
  }

  private DataWipeRecordDto toDataWipeDto(DataWipeRecord record) {
    return new DataWipeRecordDto(
        record.getId(),
        record.getItemId(),
        record.getPerformedByName(),
        record.getPerformedByEmployeeId(),
        record.getWipeMethod(),
        record.getCertificateNumber(),
        record.getNotes(),
        record.getWipedAt(),
        record.getCreatedAt());
  }

  @Override
  @Transactional
  public ItemDto completeWriteOff(UUID itemId, String documentNumber) {
    Item item = findItemOrThrow(itemId);
    validateDataWipeBeforeWriteOff(item);
    if (item.getState() != ItemState.HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN) {
      item = itemStateService.transition(item, ItemEvent.RECOMMEND_WRITE_OFF, documentNumber);
    }
    Item completed = itemStateService.transition(item, ItemEvent.APPROVE_WRITE_OFF, documentNumber);
    return itemMapper.toDto(completed);
  }

  @Override
  @Transactional
  public ItemDto receiveReclaimedPart(
      UUID parentItemId,
      String partName,
      String serialNumber,
      String inventoryNumber,
      BigDecimal estimatedValue,
      UUID warehouseBuildingId,
      UUID warehouseRoomId,
      String notes,
      String performedByUsername) {
    Item parentItem = findItemOrThrow(parentItemId);

    String resolvedInvNumber =
        (inventoryNumber != null && !inventoryNumber.isBlank())
            ? inventoryNumber.trim()
            : "PART-"
                + parentItem.getInventoryNumber()
                + "-"
                + (System.currentTimeMillis() % 10000);

    String qrCode = qrIdService.generateUniquePublicId();
    BigDecimal resolvedValue =
        estimatedValue != null
            ? estimatedValue.setScale(2, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);

    UUID resolvedBuildingId =
        warehouseBuildingId != null ? warehouseBuildingId : parentItem.getBuildingId();
    UUID resolvedRoomId = warehouseRoomId != null ? warehouseRoomId : parentItem.getRoomId();

    HashMap<String, Object> attributes = new HashMap<>();
    attributes.put("reclaimedPart", true);
    attributes.put("reclaimedFromItemId", parentItem.getId().toString());
    attributes.put("reclaimedFromInventoryNumber", parentItem.getInventoryNumber());
    if (performedByUsername != null && !performedByUsername.isBlank()) {
      attributes.put("reclaimedBy", performedByUsername);
    }
    if (notes != null && !notes.isBlank()) {
      attributes.put("reclaimNotes", notes);
    }

    Item reclaimedPartItem =
        Item.builder()
            .inventoryNumber(resolvedInvNumber)
            .serialNumber(serialNumber)
            .qrCode(qrCode)
            .name(partName)
            .description(
                "Usable part reclaimed from written-off asset "
                    + parentItem.getInventoryNumber()
                    + (notes != null ? ": " + notes : ""))
            .categoryId(parentItem.getCategoryId())
            .state(ItemState.OMBORDA)
            .purchasePrice(resolvedValue)
            .residualValue(resolvedValue)
            .replacementRecommended(false)
            .buildingId(resolvedBuildingId)
            .roomId(resolvedRoomId)
            .parentItemId(parentItem.getId())
            .customAttributes(attributes)
            .build();

    Item saved = itemRepository.save(reclaimedPartItem);
    return itemMapper.toDto(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ItemDto> findReclaimedPartsByParentItem(UUID parentItemId) {
    if (parentItemId == null) {
      return List.of();
    }
    return itemRepository.findAllByParentItemId(parentItemId).stream()
        .map(itemMapper::toDto)
        .toList();
  }

  private void evaluate50PercentRule(
      Item item, UUID ticketId, String ticketNumber, BigDecimal cost) {
    if (item == null || cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) {
      return;
    }
    BigDecimal residual =
        item.getResidualValue() != null ? item.getResidualValue() : BigDecimal.ZERO;
    boolean recommended = false;
    String reason = null;

    if (residual.compareTo(BigDecimal.ZERO) <= 0) {
      recommended = true;
      reason =
          String.format(
              "Repair cost (%s) exceeds 50%% of item's residual value (0.00). Asset is fully"
                  + " depreciated; replacement recommended.",
              cost);
    } else {
      BigDecimal halfResidual = residual.multiply(new BigDecimal("0.50"));
      if (cost.compareTo(halfResidual) >= 0) {
        recommended = true;
        reason =
            String.format(
                "Repair cost (%s) reaches or exceeds 50%% of item's residual value (%s)."
                    + " Replacement recommended.",
                cost, residual);
      }
    }

    if (recommended) {
      item.setReplacementRecommended(true);
      itemRepository.save(item);
      if (eventPublisher != null) {
        eventPublisher.publishEvent(
            new RepairReplacementRecommendedEvent(
                ticketId, ticketNumber, item.getId(), cost, residual, reason, Instant.now()));
      }
    }
  }

  private Item findItemOrThrow(UUID id) {
    return itemRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Item", id));
  }
}
