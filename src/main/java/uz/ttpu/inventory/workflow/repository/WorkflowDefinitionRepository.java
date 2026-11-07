package uz.ttpu.inventory.workflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.workflow.domain.WorkflowDefinition;
import uz.ttpu.inventory.workflow.domain.WorkflowDocumentType;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, UUID> {
    Optional<WorkflowDefinition> findByCodeIgnoreCaseAndActiveTrue(String code);
    List<WorkflowDefinition> findAllByDocumentTypeAndActiveTrue(WorkflowDocumentType documentType);
}
