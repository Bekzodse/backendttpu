package uz.ttpu.inventory.workflow.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.workflow.domain.WorkflowDocumentType;
import uz.ttpu.inventory.workflow.domain.WorkflowInstance;
import uz.ttpu.inventory.workflow.domain.WorkflowState;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, UUID> {
    Optional<WorkflowInstance> findByDocumentTypeAndDocumentId(WorkflowDocumentType documentType, UUID documentId);
    List<WorkflowInstance> findAllByState(WorkflowState state);
}
