package uz.ttpu.inventory.audit.service;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.audit.api.AuditRecordDto;
import uz.ttpu.inventory.audit.domain.AuditRecord;
import uz.ttpu.inventory.audit.repository.AuditRecordRepository;
import uz.ttpu.inventory.common.domain.AuditActionType;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditRecordRepository auditRecordRepository;
    private final AuditMapper auditMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditRecordDto recordItemAction(
            AuditActionType actionType,
            String targetId,
            String methodName,
            String actorUsername,
            String details
    ) {
        String safeTargetId = (targetId != null && targetId.length() > 255)
                ? targetId.substring(0, 255)
                : targetId;
        String safeMethodName = (methodName != null && methodName.length() > 255)
                ? methodName.substring(0, 255)
                : methodName;
        String safeActor = (actorUsername != null && actorUsername.length() > 255)
                ? actorUsername.substring(0, 255)
                : actorUsername;

        AuditRecord record = AuditRecord.builder()
                .actionType(actionType)
                .targetType("ITEM_CARD")
                .targetId(safeTargetId)
                .methodName(safeMethodName)
                .actorUsername(safeActor)
                .details(details)
                .occurredAt(Instant.now())
                .build();
        return auditMapper.toDto(auditRecordRepository.saveAndFlush(record));
    }

    @Transactional(readOnly = true)
    public List<AuditRecordDto> listAuditRecords(String targetId, AuditActionType actionType) {
        List<AuditRecord> records;
        if (targetId != null && !targetId.isBlank()) {
            records = auditRecordRepository.findAllByTargetIdOrderByOccurredAtDesc(targetId);
        } else if (actionType != null) {
            records = auditRecordRepository.findAllByActionTypeOrderByOccurredAtDesc(actionType);
        } else {
            records = auditRecordRepository.findAll();
        }
        return records.stream().map(auditMapper::toDto).toList();
    }
}
