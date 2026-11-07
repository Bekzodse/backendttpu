package uz.ttpu.inventory.inventory.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.inventory.domain.InventorySessionRoom;

public interface InventorySessionRoomRepository extends JpaRepository<InventorySessionRoom, UUID> {

  List<InventorySessionRoom> findAllBySessionIdOrderByCreatedAtAsc(UUID sessionId);

  Optional<InventorySessionRoom> findBySessionIdAndRoomId(UUID sessionId, UUID roomId);
}
