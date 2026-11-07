package uz.ttpu.inventory.references.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import uz.ttpu.inventory.references.domain.Room;

public interface RoomRepository extends JpaRepository<Room, UUID>, JpaSpecificationExecutor<Room> {
    List<Room> findAllByFloorIdAndActiveTrue(UUID floorId);
    List<Room> findAllByDepartmentIdAndActiveTrue(UUID departmentId);
}
