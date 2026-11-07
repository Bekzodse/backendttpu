package uz.ttpu.inventory.inventory.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.inventory.domain.InventoryScanAction;
import uz.ttpu.inventory.inventory.domain.ReconciliationCategory;

public interface InventoryScanActionRepository extends JpaRepository<InventoryScanAction, UUID> {

  Optional<InventoryScanAction> findByClientActionId(UUID clientActionId);

  List<InventoryScanAction> findAllBySessionIdOrderByScannedAtAsc(UUID sessionId);

  List<InventoryScanAction> findAllBySessionIdAndReconciliationCategoryOrderByScannedAtAsc(
      UUID sessionId, ReconciliationCategory category);
}
