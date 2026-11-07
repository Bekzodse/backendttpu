package uz.ttpu.inventory.movement.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.movement.api.IssueDispatchRequest;
import uz.ttpu.inventory.movement.api.MovementConfirmRequest;
import uz.ttpu.inventory.movement.api.MovementRecordDto;
import uz.ttpu.inventory.movement.api.MovementRejectRequest;
import uz.ttpu.inventory.movement.api.TransferInitiateRequest;
import uz.ttpu.inventory.movement.domain.MovementEventType;
import uz.ttpu.inventory.movement.domain.MovementRecord;
import uz.ttpu.inventory.movement.domain.MovementType;
import uz.ttpu.inventory.movement.repository.MovementRepository;
import uz.ttpu.inventory.security.api.TotpVerificationApi;

@Service
@RequiredArgsConstructor
public class MovementService {

    private final MovementRepository movementRepository;
    private final AssetLogisticsApi assetLogisticsApi;
    private final TotpVerificationApi totpVerificationApi;
    private final MovementMapper movementMapper;

    // =========================================================================
    // 1. ISSUE FLOW (OMBORDA -> YOLDA -> FOYDALANISHDA or back to OMBORDA)
    // =========================================================================

    @Transactional
    public MovementRecordDto dispatchIssue(IssueDispatchRequest request) {
        ItemDto item = assetLogisticsApi.resolveItemByIdOrQr(request.itemId(), request.qrCode());
        assetLogisticsApi.dispatchForIssue(item.id(), request.documentNumber());

        UUID operationId = UUID.randomUUID();
        MovementRecord dispatchedRecord = MovementRecord.builder()
                .operationId(operationId)
                .documentNumber(request.documentNumber())
                .movementType(MovementType.ISSUE)
                .eventType(MovementEventType.ISSUE_DISPATCHED)
                .itemId(item.id())
                .fromBuildingId(item.buildingId())
                .toBuildingId(request.toBuildingId())
                .fromFloorId(item.floorId())
                .toFloorId(request.toFloorId())
                .fromRoomId(item.roomId())
                .toRoomId(request.toRoomId())
                .fromDepartmentId(item.departmentId())
                .toDepartmentId(request.toDepartmentId())
                .fromEmployeeId(item.custodianEmployeeId())
                .toEmployeeId(request.recipientEmployeeId())
                .actorEmployeeId(request.warehouseDispatcherEmployeeId())
                .totpVerified(false)
                .reason(request.notes())
                .recordedAt(Instant.now())
                .build();

        return movementMapper.toDto(movementRepository.save(dispatchedRecord));
    }

    @Transactional
    public MovementRecordDto confirmIssue(UUID operationId, MovementConfirmRequest request) {
        MovementRecord initial = getPendingInitialRecord(operationId, MovementEventType.ISSUE_DISPATCHED);
        validateRecipient(initial, request.recipientEmployeeId());
        verifyTotpOrThrow(request.recipientEmployeeId(), request.totpCode());

        assetLogisticsApi.confirmIssue(
                initial.getItemId(),
                initial.getToBuildingId(),
                initial.getToFloorId(),
                initial.getToRoomId(),
                initial.getToDepartmentId(),
                initial.getToEmployeeId(),
                initial.getDocumentNumber()
        );

        MovementRecord acceptedRecord = MovementRecord.builder()
                .operationId(operationId)
                .documentNumber(initial.getDocumentNumber())
                .movementType(initial.getMovementType())
                .eventType(MovementEventType.ISSUE_ACCEPTED)
                .itemId(initial.getItemId())
                .fromBuildingId(initial.getFromBuildingId())
                .toBuildingId(initial.getToBuildingId())
                .fromFloorId(initial.getFromFloorId())
                .toFloorId(initial.getToFloorId())
                .fromRoomId(initial.getFromRoomId())
                .toRoomId(initial.getToRoomId())
                .fromDepartmentId(initial.getFromDepartmentId())
                .toDepartmentId(initial.getToDepartmentId())
                .fromEmployeeId(initial.getFromEmployeeId())
                .toEmployeeId(initial.getToEmployeeId())
                .actorEmployeeId(request.recipientEmployeeId())
                .totpVerified(true)
                .reason(request.comment())
                .recordedAt(Instant.now())
                .build();

        return movementMapper.toDto(movementRepository.save(acceptedRecord));
    }

    @Transactional
    public MovementRecordDto rejectIssue(UUID operationId, MovementRejectRequest request) {
        MovementRecord initial = getPendingInitialRecord(operationId, MovementEventType.ISSUE_DISPATCHED);
        validateRecipient(initial, request.recipientEmployeeId());

        assetLogisticsApi.rejectIssue(initial.getItemId(), initial.getDocumentNumber());

        MovementRecord rejectedRecord = MovementRecord.builder()
                .operationId(operationId)
                .documentNumber(initial.getDocumentNumber())
                .movementType(initial.getMovementType())
                .eventType(MovementEventType.ISSUE_REJECTED)
                .itemId(initial.getItemId())
                .fromBuildingId(initial.getFromBuildingId())
                .toBuildingId(initial.getToBuildingId())
                .fromFloorId(initial.getFromFloorId())
                .toFloorId(initial.getToFloorId())
                .fromRoomId(initial.getFromRoomId())
                .toRoomId(initial.getToRoomId())
                .fromDepartmentId(initial.getFromDepartmentId())
                .toDepartmentId(initial.getToDepartmentId())
                .fromEmployeeId(initial.getFromEmployeeId())
                .toEmployeeId(initial.getToEmployeeId())
                .actorEmployeeId(request.recipientEmployeeId())
                .totpVerified(false)
                .reason(request.reason())
                .recordedAt(Instant.now())
                .build();

        return movementMapper.toDto(movementRepository.save(rejectedRecord));
    }

    // =========================================================================
    // 2. TRANSFER FLOW (Room-to-Room & Person-to-Person)
    // =========================================================================

    @Transactional
    public MovementRecordDto initiateTransfer(TransferInitiateRequest request) {
        if (request.transferType() != MovementType.TRANSFER_ROOM_TO_ROOM
                && request.transferType() != MovementType.TRANSFER_PERSON_TO_PERSON) {
            throw new IllegalArgumentException(
                    "Transfer type must be TRANSFER_ROOM_TO_ROOM or TRANSFER_PERSON_TO_PERSON"
            );
        }

        ItemDto item = assetLogisticsApi.resolveItemByIdOrQr(request.itemId(), request.qrCode());
        assetLogisticsApi.initiateTransfer(item.id(), request.documentNumber());

        UUID operationId = UUID.randomUUID();
        MovementRecord initiatedRecord = MovementRecord.builder()
                .operationId(operationId)
                .documentNumber(request.documentNumber())
                .movementType(request.transferType())
                .eventType(MovementEventType.TRANSFER_INITIATED)
                .itemId(item.id())
                .fromBuildingId(item.buildingId())
                .toBuildingId(request.toBuildingId() != null ? request.toBuildingId() : item.buildingId())
                .fromFloorId(item.floorId())
                .toFloorId(request.toFloorId() != null ? request.toFloorId() : item.floorId())
                .fromRoomId(item.roomId())
                .toRoomId(request.toRoomId())
                .fromDepartmentId(item.departmentId())
                .toDepartmentId(request.toDepartmentId() != null ? request.toDepartmentId() : item.departmentId())
                .fromEmployeeId(item.custodianEmployeeId())
                .toEmployeeId(request.receivingEmployeeId())
                .actorEmployeeId(request.initiatingEmployeeId())
                .totpVerified(false)
                .reason(request.notes())
                .recordedAt(Instant.now())
                .build();

        return movementMapper.toDto(movementRepository.save(initiatedRecord));
    }

    @Transactional
    public MovementRecordDto confirmTransfer(UUID operationId, MovementConfirmRequest request) {
        MovementRecord initial = getPendingInitialRecord(operationId, MovementEventType.TRANSFER_INITIATED);
        validateRecipient(initial, request.recipientEmployeeId());
        verifyTotpOrThrow(request.recipientEmployeeId(), request.totpCode());

        assetLogisticsApi.confirmTransfer(
                initial.getItemId(),
                initial.getToBuildingId(),
                initial.getToFloorId(),
                initial.getToRoomId(),
                initial.getToDepartmentId(),
                initial.getToEmployeeId(),
                initial.getDocumentNumber()
        );

        MovementRecord acceptedRecord = MovementRecord.builder()
                .operationId(operationId)
                .documentNumber(initial.getDocumentNumber())
                .movementType(initial.getMovementType())
                .eventType(MovementEventType.TRANSFER_ACCEPTED)
                .itemId(initial.getItemId())
                .fromBuildingId(initial.getFromBuildingId())
                .toBuildingId(initial.getToBuildingId())
                .fromFloorId(initial.getFromFloorId())
                .toFloorId(initial.getToFloorId())
                .fromRoomId(initial.getFromRoomId())
                .toRoomId(initial.getToRoomId())
                .fromDepartmentId(initial.getFromDepartmentId())
                .toDepartmentId(initial.getToDepartmentId())
                .fromEmployeeId(initial.getFromEmployeeId())
                .toEmployeeId(initial.getToEmployeeId())
                .actorEmployeeId(request.recipientEmployeeId())
                .totpVerified(true)
                .reason(request.comment())
                .recordedAt(Instant.now())
                .build();

        return movementMapper.toDto(movementRepository.save(acceptedRecord));
    }

    @Transactional
    public MovementRecordDto rejectTransfer(UUID operationId, MovementRejectRequest request) {
        MovementRecord initial = getPendingInitialRecord(operationId, MovementEventType.TRANSFER_INITIATED);
        validateRecipient(initial, request.recipientEmployeeId());

        assetLogisticsApi.rejectTransfer(initial.getItemId(), initial.getDocumentNumber());

        MovementRecord rejectedRecord = MovementRecord.builder()
                .operationId(operationId)
                .documentNumber(initial.getDocumentNumber())
                .movementType(initial.getMovementType())
                .eventType(MovementEventType.TRANSFER_REJECTED)
                .itemId(initial.getItemId())
                .fromBuildingId(initial.getFromBuildingId())
                .toBuildingId(initial.getToBuildingId())
                .fromFloorId(initial.getFromFloorId())
                .toFloorId(initial.getToFloorId())
                .fromRoomId(initial.getFromRoomId())
                .toRoomId(initial.getToRoomId())
                .fromDepartmentId(initial.getFromDepartmentId())
                .toDepartmentId(initial.getToDepartmentId())
                .fromEmployeeId(initial.getFromEmployeeId())
                .toEmployeeId(initial.getToEmployeeId())
                .actorEmployeeId(request.recipientEmployeeId())
                .totpVerified(false)
                .reason(request.reason())
                .recordedAt(Instant.now())
                .build();

        return movementMapper.toDto(movementRepository.save(rejectedRecord));
    }

    @Transactional(readOnly = true)
    public List<MovementRecordDto> getOperationRecords(UUID operationId) {
        return movementRepository.findAllByOperationIdOrderByRecordedAtAsc(operationId)
                .stream()
                .map(movementMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MovementRecordDto> getItemMovementHistory(UUID itemId) {
        return movementRepository.findAllByItemIdOrderByRecordedAtDesc(itemId)
                .stream()
                .map(movementMapper::toDto)
                .toList();
    }

    private MovementRecord getPendingInitialRecord(UUID operationId, MovementEventType expectedInitialType) {
        List<MovementRecord> records = movementRepository.findAllByOperationIdOrderByRecordedAtAsc(operationId);
        if (records.isEmpty()) {
            throw new ResourceNotFoundException("Movement operation", operationId);
        }
        if (records.size() > 1) {
            throw new IllegalStateException(
                    "Movement operation " + operationId + " is already completed with event "
                            + records.get(records.size() - 1).getEventType()
            );
        }
        MovementRecord initial = records.get(0);
        if (initial.getEventType() != expectedInitialType) {
            throw new IllegalStateException(
                    "Expected initial movement event " + expectedInitialType + ", but found " + initial.getEventType()
            );
        }
        return initial;
    }

    private void validateRecipient(MovementRecord initial, UUID actorRecipientId) {
        if (!initial.getToEmployeeId().equals(actorRecipientId)) {
            throw new IllegalArgumentException(
                    "Only the assigned recipient (" + initial.getToEmployeeId() + ") can confirm or reject this movement"
            );
        }
    }

    private void verifyTotpOrThrow(UUID recipientEmployeeId, String totpCode) {
        if (!totpVerificationApi.verifyRecipientTotp(recipientEmployeeId, totpCode)) {
            throw new BadCredentialsException("Invalid 2FA TOTP code for movement confirmation");
        }
    }
}
