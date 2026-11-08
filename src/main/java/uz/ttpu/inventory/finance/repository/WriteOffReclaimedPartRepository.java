package uz.ttpu.inventory.finance.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.finance.domain.WriteOffReclaimedPart;

public interface WriteOffReclaimedPartRepository
    extends JpaRepository<WriteOffReclaimedPart, UUID> {

  List<WriteOffReclaimedPart> findAllByWrittenOffItemIdOrderByReclaimedAtDesc(
      UUID writtenOffItemId);

  List<WriteOffReclaimedPart> findAllByReclaimedItemId(UUID reclaimedItemId);
}
