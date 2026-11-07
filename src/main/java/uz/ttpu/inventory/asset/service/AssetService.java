package uz.ttpu.inventory.asset.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.asset.api.ItemStateHistoryDto;
import uz.ttpu.inventory.asset.api.PilotItemUpsertRequest;
import uz.ttpu.inventory.asset.api.PublicItemCardDto;
import uz.ttpu.inventory.asset.api.TransitionItemRequest;
import uz.ttpu.inventory.asset.domain.IllegalStateTransitionException;
import uz.ttpu.inventory.asset.domain.Item;
import uz.ttpu.inventory.asset.domain.ItemEvent;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.asset.repository.ItemRepository;
import uz.ttpu.inventory.asset.repository.ItemStateHistoryRepository;
import uz.ttpu.inventory.common.domain.AuditActionType;
import uz.ttpu.inventory.common.domain.AuditableItemAction;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.receipt.api.ReceiptApprovedEvent;
import uz.ttpu.inventory.repair.api.ItemMovedToVendorRepairEvent;
import uz.ttpu.inventory.repair.api.ItemRepairCompletedEvent;
import uz.ttpu.inventory.workflow.api.WorkflowCompletedEvent;

@Service
@RequiredArgsConstructor
public class AssetService implements AssetLogisticsApi {

    private final ItemRepository itemRepository;
    private final ItemStateHistoryRepository itemStateHistoryRepository;
    private final ItemStateService itemStateService;
    private final QrIdService qrIdService;
    private final ItemMapper itemMapper;

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
        Item item = itemRepository.findByQrCodeIgnoreCase(normalizedQrId)
                .orElseGet(() -> itemRepository.findByQrCode(normalizedQrId)
                        .orElseThrow(() -> new ResourceNotFoundException("Item not found with QR ID: " + qrId)));
        return new PublicItemCardDto(
                item.getQrCode(),
                item.getName(),
                item.getInventoryNumber(),
                PublicItemCardDto.DEFAULT_OWNERSHIP_LABEL,
                publicContactPhone
        );
    }

    @Transactional
    @AuditableItemAction(AuditActionType.CHANGE)
    public ItemDto createItem(CreateItemRequest request) {
        String qrCode = (request.qrCode() != null && !request.qrCode().isBlank())
                ? request.qrCode()
                : qrIdService.generateUniquePublicId();

        Item item = Item.builder()
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
                .customAttributes(request.customAttributes() != null ? new HashMap<>(request.customAttributes()) : new HashMap<>())
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
        item.setCustomAttributes(request.customAttributes() != null ? new HashMap<>(request.customAttributes()) : new HashMap<>());
        item.setOneCRefId(request.oneCRefId());
        return itemMapper.toDto(itemRepository.save(item));
    }

    @Transactional
    @AuditableItemAction(AuditActionType.CHANGE)
    public ItemDto transitionItemState(UUID id, TransitionItemRequest request) {
        Item item = findItemOrThrow(id);
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
                String invNumber = "TTPU-" + event.receiptNumber() + "-" + String.format("%03d", lineSequence++);
                String serial = count == 1 ? line.serialNumber() : line.serialNumber() + "-" + i;

                Item item = Item.builder()
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
        Item updated = itemStateService.transition(item, ItemEvent.SEND_TO_REPAIR, event.ticketNumber());
        return itemMapper.toDto(updated);
    }

    @EventListener
    @Transactional
    @AuditableItemAction(AuditActionType.CHANGE)
    public ItemDto onItemRepairCompleted(ItemRepairCompletedEvent event) {
        Item item = findItemOrThrow(event.itemId());
        if (item.getState() == ItemState.TAMIRDA) {
            item.setExpectedReturnAt(null);
            Item updated = itemStateService.transition(item, ItemEvent.COMPLETE_REPAIR, event.ticketNumber());
            return itemMapper.toDto(updated);
        }
        return itemMapper.toDto(item);
    }

    @EventListener
    @Transactional
    @AuditableItemAction(AuditActionType.CHANGE)
    public Optional<ItemDto> onWorkflowCompleted(WorkflowCompletedEvent event) {
        if (!event.isWriteOffDocument() || event.documentId() == null) {
            return Optional.empty();
        }
        return itemRepository.findById(event.documentId()).map(item -> {
            if (item.getState() == ItemState.HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN) {
                String docRef = event.documentNumber() != null && !event.documentNumber().isBlank()
                        ? event.documentNumber()
                        : event.instanceId().toString();
                if (event.isApproved()) {
                    return itemMapper.toDto(itemStateService.transition(item, ItemEvent.APPROVE_WRITE_OFF, docRef));
                } else if (event.isRejected()) {
                    return itemMapper.toDto(itemStateService.transition(item, ItemEvent.REJECT_WRITE_OFF, docRef));
                }
            }
            return itemMapper.toDto(item);
        });
    }

    @Transactional(readOnly = true)
    public List<ItemStateHistoryDto> getItemHistory(UUID id) {
        findItemOrThrow(id);
        return itemStateHistoryRepository.findAllByItemIdOrderByTransitionedAtDesc(id)
                .stream()
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
            Item item = itemRepository.findByQrCodeIgnoreCase(qrCode.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Item not found with QR code: " + qrCode));
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
        return itemRepository.findAll().stream()
                .map(itemMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemDto> findExpectedItemsForRooms(Collection<UUID> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return List.of();
        }
        return itemRepository.findAllByRoomIdInAndStateNot(roomIds, ItemState.HISOBDAN_CHIQARILGAN)
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
            UUID itemId,
            String inventoryNumber,
            String oneCRefId,
            BigDecimal newResidualValue
    ) {
        Item item;
        if (itemId != null) {
            item = findItemOrThrow(itemId);
        } else if (inventoryNumber != null && !inventoryNumber.isBlank()) {
            item = itemRepository.findByInventoryNumberIgnoreCase(inventoryNumber.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Item not found with inventoryNumber: " + inventoryNumber));
        } else {
            throw new IllegalArgumentException("Either itemId or inventoryNumber must be provided for 1C depreciation");
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
                    "Issue requires item to be in OMBORDA state, but was " + item.getState()
            );
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
            String documentNumber
    ) {
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
                    "Transfer requires item to be in FOYDALANISHDA state, but was " + item.getState()
            );
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
            String documentNumber
    ) {
        return confirmIssue(itemId, toBuildingId, toFloorId, toRoomId, toDepartmentId, toEmployeeId, documentNumber);
    }

    @Transactional(readOnly = true)
    @AuditableItemAction(AuditActionType.READ)
    public AssetSearchResultDto searchItems(AssetSearchFilterRequest filter, int page, int size) {
        long startNanos = System.nanoTime();
        int safePage = Math.max(0, page);
        int safeSize = Math.min(200, Math.max(1, size));
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "inventoryNumber"));
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
                elapsedMs
        );
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
    @Transactional
    @AuditableItemAction(AuditActionType.CHANGE)
    public ItemDto upsertPilotMigratedItem(PilotItemUpsertRequest request) {
        Item item = itemRepository.findByInventoryNumberIgnoreCase(request.inventoryNumber().trim())
                .orElseGet(() -> Item.builder()
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
        item.setPurchasePrice(request.purchasePrice() != null ? request.purchasePrice() : BigDecimal.ZERO);
        item.setResidualValue(request.residualValue() != null ? request.residualValue() : BigDecimal.ZERO);
        item.setCommissionedAt(request.commissionedAt());
        item.setOneCRefId(request.oneCRefId());
        if (request.customAttributes() != null) {
            item.setCustomAttributes(new HashMap<>(request.customAttributes()));
        }

        Item saved = itemRepository.save(item);
        if (saved.getState() == ItemState.OMBORDA && saved.getRoomId() != null) {
            Item dispatched = itemStateService.transition(saved, ItemEvent.DISPATCH, "PILOT-1C-MIGRATION");
            saved = itemStateService.transition(dispatched, ItemEvent.PUT_INTO_USE, "PILOT-1C-MIGRATION");
        }
        return itemMapper.toDto(saved);
    }

    private Item findItemOrThrow(UUID id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item", id));
    }
}
