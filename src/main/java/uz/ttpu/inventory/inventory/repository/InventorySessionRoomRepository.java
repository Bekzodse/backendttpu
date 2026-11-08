package uz.ttpu.inventory.inventory.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.ttpu.inventory.inventory.domain.InventorySessionRoom;
import uz.ttpu.inventory.inventory.domain.InventorySessionStatus;

public interface InventorySessionRoomRepository extends JpaRepository<InventorySessionRoom, UUID> {

  List<InventorySessionRoom> findAllBySessionIdOrderByCreatedAtAsc(UUID sessionId);

  Optional<InventorySessionRoom> findBySessionIdAndRoomId(UUID sessionId, UUID roomId);

  @Query(
      "SELECT r FROM InventorySessionRoom r JOIN FETCH r.session s WHERE r.roomId = :roomId AND"
          + " s.status = :status")
  List<InventorySessionRoom> findActiveByRoomIdAndStatus(
      @Param("roomId") UUID roomId, @Param("status") InventorySessionStatus status);
}
