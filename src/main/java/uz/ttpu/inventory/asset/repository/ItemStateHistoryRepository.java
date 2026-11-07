package uz.ttpu.inventory.asset.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.asset.domain.ItemStateHistory;

public interface ItemStateHistoryRepository extends JpaRepository<ItemStateHistory, UUID> {
  List<ItemStateHistory> findAllByItemIdOrderByTransitionedAtDesc(UUID itemId);
}
