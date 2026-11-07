package uz.ttpu.inventory.movement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.movement.domain.OffCampusPermit;
import uz.ttpu.inventory.movement.domain.PermitStatus;

public interface OffCampusPermitRepository extends JpaRepository<OffCampusPermit, UUID> {
  Optional<OffCampusPermit> findByPermitNumber(String permitNumber);

  Optional<OffCampusPermit> findTopByQrCodeIgnoreCaseAndStatus(String qrCode, PermitStatus status);

  Optional<OffCampusPermit> findTopByItemIdAndStatus(UUID itemId, PermitStatus status);

  List<OffCampusPermit> findAllByItemIdOrderByCreatedAtDesc(UUID itemId);

  List<OffCampusPermit> findAllByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
}
