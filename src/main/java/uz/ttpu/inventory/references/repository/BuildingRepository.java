package uz.ttpu.inventory.references.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import uz.ttpu.inventory.references.domain.Building;

public interface BuildingRepository extends JpaRepository<Building, UUID>, JpaSpecificationExecutor<Building> {
    Optional<Building> findByCodeIgnoreCase(String code);
    List<Building> findAllByActiveTrue();
}
