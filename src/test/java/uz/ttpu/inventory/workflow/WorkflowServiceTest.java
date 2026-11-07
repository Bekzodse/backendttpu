package uz.ttpu.inventory.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import uz.ttpu.inventory.workflow.api.StartWorkflowInstanceRequest;
import uz.ttpu.inventory.workflow.api.StepApprovalRequest;
import uz.ttpu.inventory.workflow.api.StepRejectionRequest;
import uz.ttpu.inventory.workflow.api.StepReworkRequest;
import uz.ttpu.inventory.workflow.api.WorkflowCompletedEvent;
import uz.ttpu.inventory.workflow.api.WorkflowInstanceDto;
import uz.ttpu.inventory.workflow.api.WorkflowReturnedForReworkEvent;
import uz.ttpu.inventory.workflow.domain.VerificationMethod;
import uz.ttpu.inventory.workflow.domain.WorkflowDecisionType;
import uz.ttpu.inventory.workflow.domain.WorkflowDefinition;
import uz.ttpu.inventory.workflow.domain.WorkflowDocumentType;
import uz.ttpu.inventory.workflow.domain.WorkflowInstance;
import uz.ttpu.inventory.workflow.domain.WorkflowState;
import uz.ttpu.inventory.workflow.domain.WorkflowStepDecision;
import uz.ttpu.inventory.workflow.domain.WorkflowStepDefinition;
import uz.ttpu.inventory.workflow.repository.WorkflowDefinitionRepository;
import uz.ttpu.inventory.workflow.repository.WorkflowInstanceRepository;
import uz.ttpu.inventory.workflow.service.WorkflowMapper;
import uz.ttpu.inventory.workflow.service.WorkflowService;

class WorkflowServiceTest {

    private WorkflowDefinitionRepository definitionRepository;
    private WorkflowInstanceRepository instanceRepository;
    private ApplicationEventPublisher eventPublisher;
    private WorkflowService workflowService;

    private final Map<UUID, WorkflowInstance> instanceStore = new HashMap<>();
    private WorkflowDefinition writeOffDefinition;

    @BeforeEach
    void setUp() {
        definitionRepository = mock(WorkflowDefinitionRepository.class);
        instanceRepository = mock(WorkflowInstanceRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        WorkflowMapper mapper = Mappers.getMapper(WorkflowMapper.class);

        writeOffDefinition = WorkflowDefinition.builder()
                .code("WF-WRITE-OFF")
                .name("Asset Write-Off Approval")
                .documentType(WorkflowDocumentType.WRITE_OFF)
                .build();
        writeOffDefinition.setId(UUID.randomUUID());
        writeOffDefinition.setActive(true);

        // Step 1: Department Head (TOTP)
        WorkflowStepDefinition step1 = WorkflowStepDefinition.builder()
                .stepOrder(1)
                .stepName("Department Head Review")
                .approverRole("ROLE_DEPARTMENT_HEAD")
                .requiredVerificationMethod(VerificationMethod.TOTP)
                .build();
        step1.setId(UUID.randomUUID());
        writeOffDefinition.addStep(step1);

        // Step 2: Chief Accountant (E-IMZO)
        WorkflowStepDefinition step2 = WorkflowStepDefinition.builder()
                .stepOrder(2)
                .stepName("Chief Accountant Sign-Off")
                .approverRole("ROLE_ACCOUNTANT")
                .requiredVerificationMethod(VerificationMethod.E_IMZO)
                .build();
        step2.setId(UUID.randomUUID());
        writeOffDefinition.addStep(step2);

        // Step 3: Rector Approval (E-IMZO) - ONLY required if amount >= 50,000,000.00
        WorkflowStepDefinition step3 = WorkflowStepDefinition.builder()
                .stepOrder(3)
                .stepName("Rector Final Approval")
                .approverRole("ROLE_RECTOR")
                .requiredVerificationMethod(VerificationMethod.E_IMZO)
                .minAmountCondition(new BigDecimal("50000000.00"))
                .build();
        step3.setId(UUID.randomUUID());
        writeOffDefinition.addStep(step3);

        when(definitionRepository.findByCodeIgnoreCaseAndActiveTrue("WF-WRITE-OFF"))
                .thenReturn(Optional.of(writeOffDefinition));

        when(instanceRepository.save(any(WorkflowInstance.class))).thenAnswer(inv -> {
            WorkflowInstance inst = inv.getArgument(0);
            if (inst.getId() == null) {
                inst.setId(UUID.randomUUID());
            }
            for (WorkflowStepDecision dec : inst.getDecisions()) {
                if (dec.getId() == null) {
                    dec.setId(UUID.randomUUID());
                }
            }
            instanceStore.put(inst.getId(), inst);
            return inst;
        });

        when(instanceRepository.findById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(instanceStore.get(id));
        });

        workflowService = new WorkflowService(definitionRepository, instanceRepository, mapper, eventPublisher);
    }

    @Test
    void multiStepApprovalWithTotpAndEImzoRespectsConditionsAndCompletesWorkflow() {
        // High-value write-off (65,000,000.00 >= 50,000,000.00) -> all 3 steps apply
        WorkflowInstanceDto started = workflowService.startInstance(new StartWorkflowInstanceRequest(
                "WF-WRITE-OFF",
                UUID.randomUUID(),
                "WO-2026-001",
                new BigDecimal("65000000.00"),
                Map.of("department", "IT"),
                UUID.randomUUID(),
                true
        ));

        assertThat(started.state()).isEqualTo(WorkflowState.IN_AGREEMENT);
        assertThat(started.currentStepOrder()).isEqualTo(1);

        // Step 1 requires TOTP; passing E_IMZO must fail
        assertThatThrownBy(() -> workflowService.approveStep(
                started.id(),
                new StepApprovalRequest(VerificationMethod.E_IMZO, "PKCS7-SIG", "ok", UUID.randomUUID(), "dept.head")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires verification method TOTP");

        // Approve Step 1 with valid 6-digit TOTP
        WorkflowInstanceDto afterStep1 = workflowService.approveStep(
                started.id(),
                new StepApprovalRequest(VerificationMethod.TOTP, "482910", "Confirmed by Dept Head", UUID.randomUUID(), "dept.head")
        );
        assertThat(afterStep1.state()).isEqualTo(WorkflowState.IN_AGREEMENT);
        assertThat(afterStep1.currentStepOrder()).isEqualTo(2);

        // Approve Step 2 with E-IMZO signature
        WorkflowInstanceDto afterStep2 = workflowService.approveStep(
                started.id(),
                new StepApprovalRequest(VerificationMethod.E_IMZO, "MIIBmQYJKoZIhvcNAQcCoIIBijCCAYYCAQEx...", "Verified in 1C", UUID.randomUUID(), "chief.accountant")
        );
        assertThat(afterStep2.state()).isEqualTo(WorkflowState.IN_AGREEMENT);
        assertThat(afterStep2.currentStepOrder()).isEqualTo(3);

        // Approve Step 3 (Rector) with E-IMZO signature -> transitions to APPROVED
        WorkflowInstanceDto afterStep3 = workflowService.approveStep(
                started.id(),
                new StepApprovalRequest(VerificationMethod.E_IMZO, "MIIBmQYJKoZIhvcNAQcCoIIBijCCAYYCAQEx-RECTOR", "Approved by Rector", UUID.randomUUID(), "rector")
        );
        assertThat(afterStep3.state()).isEqualTo(WorkflowState.APPROVED);
        assertThat(afterStep3.currentStepOrder()).isNull();
        assertThat(afterStep3.completedAt()).isNotNull();
        assertThat(afterStep3.decisions()).hasSize(3);

        ArgumentCaptor<WorkflowCompletedEvent> eventCaptor = ArgumentCaptor.forClass(WorkflowCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().finalState()).isEqualTo(WorkflowState.APPROVED);
    }

    @Test
    void skipsConditionalRectorStepWhenAmountIsBelowThreshold() {
        // Low-value write-off (10,000,000.00 < 50,000,000.00) -> only steps 1 and 2 apply!
        WorkflowInstanceDto instance = workflowService.startInstance(new StartWorkflowInstanceRequest(
                "WF-WRITE-OFF",
                UUID.randomUUID(),
                "WO-2026-002",
                new BigDecimal("10000000.00"),
                Map.of(),
                UUID.randomUUID(),
                true
        ));

        workflowService.approveStep(
                instance.id(),
                new StepApprovalRequest(VerificationMethod.TOTP, "123456", "Step 1 ok", UUID.randomUUID(), "dept.head")
        );

        WorkflowInstanceDto completed = workflowService.approveStep(
                instance.id(),
                new StepApprovalRequest(VerificationMethod.E_IMZO, "PKCS7-ACCOUNTANT-SIG", "Step 2 ok", UUID.randomUUID(), "accountant")
        );

        assertThat(completed.state()).isEqualTo(WorkflowState.APPROVED);
        assertThat(completed.decisions()).hasSize(2);
    }

    @Test
    void supportsReturnForReworkResubmissionAndRejectionWithReason() {
        WorkflowInstanceDto draft = workflowService.startInstance(new StartWorkflowInstanceRequest(
                "WF-WRITE-OFF",
                UUID.randomUUID(),
                "WO-2026-003",
                new BigDecimal("12000000.00"),
                Map.of(),
                UUID.randomUUID(),
                false
        ));
        assertThat(draft.state()).isEqualTo(WorkflowState.DRAFT);

        WorkflowInstanceDto submitted = workflowService.submitInstance(draft.id());
        assertThat(submitted.state()).isEqualTo(WorkflowState.IN_AGREEMENT);
        assertThat(submitted.currentStepOrder()).isEqualTo(1);

        // Return for rework at Step 1 -> moves back to DRAFT with reworkReason
        WorkflowInstanceDto reworked = workflowService.returnStepForRework(
                submitted.id(),
                new StepReworkRequest("Attach technical inspection act photo", UUID.randomUUID(), "dept.head")
        );
        assertThat(reworked.state()).isEqualTo(WorkflowState.DRAFT);
        assertThat(reworked.reworkReason()).isEqualTo("Attach technical inspection act photo");
        assertThat(reworked.currentStepOrder()).isNull();
        verify(eventPublisher).publishEvent(any(WorkflowReturnedForReworkEvent.class));

        // Re-submit after rework -> moves back to IN_AGREEMENT at Step 1
        WorkflowInstanceDto resubmitted = workflowService.submitInstance(reworked.id());
        assertThat(resubmitted.state()).isEqualTo(WorkflowState.IN_AGREEMENT);
        assertThat(resubmitted.currentStepOrder()).isEqualTo(1);
        assertThat(resubmitted.reworkReason()).isNull();

        // Reject at Step 1 with mandatory reason -> moves to REJECTED
        WorkflowInstanceDto rejected = workflowService.rejectStep(
                resubmitted.id(),
                new StepRejectionRequest("Item is still functional and under warranty", UUID.randomUUID(), "dept.head")
        );
        assertThat(rejected.state()).isEqualTo(WorkflowState.REJECTED);
        assertThat(rejected.rejectionReason()).isEqualTo("Item is still functional and under warranty");
        assertThat(rejected.completedAt()).isNotNull();
        assertThat(rejected.decisions())
                .extracting(d -> d.decision())
                .containsExactly(WorkflowDecisionType.RETURNED_FOR_REWORK, WorkflowDecisionType.REJECTED);
    }
}
