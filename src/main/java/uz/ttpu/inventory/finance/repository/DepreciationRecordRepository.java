package uz.ttpu.inventory.finance.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.finance.domain.DepreciationRecord;

public interface DepreciationRecordRepository extends JpaRepository<DepreciationRecord, UUID> {

  List<DepreciationRecord> findAllByItemIdOrderByCalculatedAtDesc(UUID itemId);

  java.util.Optional<DepreciationRecord> findTopByItemIdOrderByCalculatedAtDesc(UUID itemId);
}
