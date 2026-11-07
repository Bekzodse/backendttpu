package uz.ttpu.inventory.references.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import uz.ttpu.inventory.references.domain.Employee;

public interface EmployeeRepository
    extends JpaRepository<Employee, UUID>, JpaSpecificationExecutor<Employee> {
  Optional<Employee> findByEmployeeCodeIgnoreCase(String employeeCode);

  Optional<Employee> findByHemisId(String hemisId);

  Optional<Employee> findByEmailIgnoreCase(String email);

  List<Employee> findAllByDepartmentIdAndActiveTrue(UUID departmentId);
}
