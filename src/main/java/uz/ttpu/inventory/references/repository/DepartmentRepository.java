package uz.ttpu.inventory.references.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import uz.ttpu.inventory.references.domain.Department;

public interface DepartmentRepository
    extends JpaRepository<Department, UUID>, JpaSpecificationExecutor<Department> {
  Optional<Department> findByCodeIgnoreCase(String code);

  List<Department> findAllByActiveTrue();
}
