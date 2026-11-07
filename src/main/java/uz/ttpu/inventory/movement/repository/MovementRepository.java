package uz.ttpu.inventory.movement.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.movement.domain.MovementRecord;

public interface MovementRepository extends JpaRepository<MovementRecord, UUID> {
  List<MovementRecord> findAllByOperationIdOrderByRecordedAtAsc(UUID operationId);

  List<MovementRecord> findAllByItemIdOrderByRecordedAtDesc(UUID itemId);
}
