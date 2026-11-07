package uz.ttpu.inventory.asset.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.asset.domain.DataWipeRecord;

public interface DataWipeRecordRepository extends JpaRepository<DataWipeRecord, UUID> {
  boolean existsByItemId(UUID itemId);

  Optional<DataWipeRecord> findTopByItemIdOrderByWipedAtDesc(UUID itemId);

  List<DataWipeRecord> findAllByItemIdOrderByWipedAtDesc(UUID itemId);
}
