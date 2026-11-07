package uz.ttpu.inventory.references.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import uz.ttpu.inventory.references.domain.Floor;

public interface FloorRepository
    extends JpaRepository<Floor, UUID>, JpaSpecificationExecutor<Floor> {
  List<Floor> findAllByBuildingIdAndActiveTrue(UUID buildingId);
}
