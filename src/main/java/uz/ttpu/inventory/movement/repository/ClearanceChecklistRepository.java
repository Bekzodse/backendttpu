package uz.ttpu.inventory.movement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.movement.domain.ClearanceChecklist;
import uz.ttpu.inventory.movement.domain.ClearanceChecklistStatus;

public interface ClearanceChecklistRepository extends JpaRepository<ClearanceChecklist, UUID> {
  Optional<ClearanceChecklist> findByChecklistNumber(String checklistNumber);

  List<ClearanceChecklist> findAllByEmployeeId(UUID employeeId);

  Optional<ClearanceChecklist> findFirstByEmployeeIdAndStatus(
      UUID employeeId, ClearanceChecklistStatus status);

  List<ClearanceChecklist> findAllByStatus(ClearanceChecklistStatus status);
}
