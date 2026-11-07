package uz.ttpu.inventory.audit.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.audit.domain.AuditRecord;
import uz.ttpu.inventory.common.domain.AuditActionType;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID> {
  List<AuditRecord> findAllByTargetIdOrderByOccurredAtDesc(String targetId);

  List<AuditRecord> findAllByActionTypeOrderByOccurredAtDesc(AuditActionType actionType);
}
