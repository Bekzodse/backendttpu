package uz.ttpu.inventory.inventory.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.inventory.domain.InventorySession;

public interface InventorySessionRepository extends JpaRepository<InventorySession, UUID> {

  Optional<InventorySession> findBySessionCodeIgnoreCase(String sessionCode);
}
