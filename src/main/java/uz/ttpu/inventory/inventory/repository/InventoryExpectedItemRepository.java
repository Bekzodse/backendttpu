package uz.ttpu.inventory.inventory.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.inventory.domain.InventoryExpectedItem;
import uz.ttpu.inventory.inventory.domain.InventoryLineStatus;

public interface InventoryExpectedItemRepository extends JpaRepository<InventoryExpectedItem, UUID> {

    List<InventoryExpectedItem> findAllBySessionIdOrderByInventoryNumberAsc(UUID sessionId);

    List<InventoryExpectedItem> findAllBySessionIdAndRoomIdOrderByInventoryNumberAsc(UUID sessionId, UUID roomId);

    Optional<InventoryExpectedItem> findBySessionIdAndItemId(UUID sessionId, UUID itemId);

    Optional<InventoryExpectedItem> findBySessionIdAndQrCodeIgnoreCase(UUID sessionId, String qrCode);

    Optional<InventoryExpectedItem> findBySessionIdAndInventoryNumberIgnoreCase(UUID sessionId, String inventoryNumber);

    long countBySessionIdAndRoomIdAndLineStatus(UUID sessionId, UUID roomId, InventoryLineStatus lineStatus);
}
