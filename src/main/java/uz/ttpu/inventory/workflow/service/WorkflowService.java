package uz.ttpu.inventory.workflow.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.workflow.api.CreateWorkflowDefinitionRequest;
import uz.ttpu.inventory.workflow.api.StartWorkflowInstanceRequest;
import uz.ttpu.inventory.workflow.api.StepApprovalRequest;
import uz.ttpu.inventory.workflow.api.StepRejectionRequest;
import uz.ttpu.inventory.workflow.api.StepReworkRequest;
import uz.ttpu.inventory.workflow.api.WorkflowCompletedEvent;
import uz.ttpu.inventory.workflow.api.WorkflowDefinitionDto;
import uz.ttpu.inventory.workflow.api.WorkflowInstanceDto;
import uz.ttpu.inventory.workflow.api.WorkflowReturnedForReworkEvent;
import uz.ttpu.inventory.workflow.api.WorkflowStepDefinitionRequest;
import uz.ttpu.inventory.workflow.domain.VerificationMethod;
import uz.ttpu.inventory.workflow.domain.WorkflowDecisionType;
import uz.ttpu.inventory.workflow.domain.WorkflowDefinition;
import uz.ttpu.inventory.workflow.domain.WorkflowInstance;
import uz.ttpu.inventory.workflow.domain.WorkflowState;
import uz.ttpu.inventory.workflow.domain.WorkflowStepDecision;
import uz.ttpu.inventory.workflow.domain.WorkflowStepDefinition;
import uz.ttpu.inventory.workflow.repository.WorkflowDefinitionRepository;
import uz.ttpu.inventory.workflow.repository.WorkflowInstanceRepository;

@Service
@RequiredArgsConstructor
public class WorkflowService {

  private final WorkflowDefinitionRepository definitionRepository;
  private final WorkflowInstanceRepository instanceRepository;
  private final WorkflowMapper workflowMapper;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public List<WorkflowDefinitionDto> listDefinitions() {
    return definitionRepository.findAll().stream().map(workflowMapper::toDefinitionDto).toList();
  }

  @Transactional
  public WorkflowDefinitionDto createDefinition(CreateWorkflowDefinitionRequest request) {
    Set<Integer> seenOrders = new HashSet<>();
    List<WorkflowStepDefinitionRequest> sortedSteps =
        request.steps().stream()
            .sorted(Comparator.comparingInt(WorkflowStepDefinitionRequest::stepOrder))
            .toList();

    for (WorkflowStepDefinitionRequest stepReq : sortedSteps) {
      if (!seenOrders.add(stepReq.stepOrder())) {
        throw new IllegalArgumentException(
            "Duplicate stepOrder " + stepReq.stepOrder() + " in workflow definition");
      }
    }

    WorkflowDefinition definition =
        WorkflowDefinition.builder()
            .code(request.code())
            .name(request.name())
            .documentType(request.documentType())
            .description(request.description())
            .build();
    definition.setActive(true);

    for (WorkflowStepDefinitionRequest stepReq : sortedSteps) {
      WorkflowStepDefinition step =
          WorkflowStepDefinition.builder()
              .stepOrder(stepReq.stepOrder())
              .stepName(stepReq.stepName())
              .approverRole(stepReq.approverRole())
              .requiredVerificationMethod(stepReq.requiredVerificationMethod())
              .minAmountCondition(stepReq.minAmountCondition())
              .maxAmountCondition(stepReq.maxAmountCondition())
              .conditionAttributes(
                  stepReq.conditionAttributes() != null
                      ? new HashMap<>(stepReq.conditionAttributes())
                      : new HashMap<>())
              .build();
      definition.addStep(step);
    }

    return workflowMapper.toDefinitionDto(definitionRepository.save(definition));
  }

  @Transactional(readOnly = true)
  public WorkflowInstanceDto getInstance(UUID instanceId) {
    return workflowMapper.toInstanceDto(findInstanceOrThrow(instanceId));
  }

  @Transactional
  public WorkflowInstanceDto startInstance(StartWorkflowInstanceRequest request) {
    WorkflowDefinition definition =
        definitionRepository
            .findByCodeIgnoreCaseAndActiveTrue(request.definitionCode())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Active workflow definition not found: " + request.definitionCode()));

    WorkflowInstance instance =
        WorkflowInstance.builder()
            .definition(definition)
            .documentType(definition.getDocumentType())
            .documentId(request.documentId())
            .documentNumber(request.documentNumber())
            .amount(request.amount() != null ? request.amount() : BigDecimal.ZERO)
            .contextAttributes(
                request.contextAttributes() != null
                    ? new HashMap<>(request.contextAttributes())
                    : new HashMap<>())
            .state(WorkflowState.DRAFT)
            .initiatedByUserId(request.initiatedByUserId())
            .build();

    WorkflowInstance saved = instanceRepository.save(instance);
    if (request.submitImmediately()) {
      return submitInstance(saved.getId());
    }
    return workflowMapper.toInstanceDto(saved);
  }

  @Transactional
  public WorkflowInstanceDto submitInstance(UUID instanceId) {
    WorkflowInstance instance = findInstanceOrThrow(instanceId);
    if (instance.getState() != WorkflowState.DRAFT) {
      throw new IllegalStateException(
          "Only DRAFT workflow instances can be submitted. Current state: " + instance.getState());
    }

    Instant now = Instant.now();
    instance.setSubmittedAt(now);
    instance.setReworkReason(null);
    instance.setRejectionReason(null);

    List<WorkflowStepDefinition> applicableSteps = resolveApplicableSteps(instance);
    if (applicableSteps.isEmpty()) {
      instance.setState(WorkflowState.APPROVED);
      instance.setCurrentStepOrder(null);
      instance.setCompletedAt(now);
      WorkflowInstance saved = instanceRepository.save(instance);
      publishCompletedEvent(saved, null, now);
      return workflowMapper.toInstanceDto(saved);
    }

    instance.setState(WorkflowState.IN_AGREEMENT);
    instance.setCurrentStepOrder(applicableSteps.get(0).getStepOrder());
    return workflowMapper.toInstanceDto(instanceRepository.save(instance));
  }

  @Transactional
  public WorkflowInstanceDto approveStep(UUID instanceId, StepApprovalRequest request) {
    WorkflowInstance instance = findInstanceOrThrow(instanceId);
    ensureInAgreement(instance);

    WorkflowStepDefinition currentStep = findCurrentStepOrThrow(instance);
    validateVerification(currentStep, request);

    Instant now = Instant.now();
    WorkflowStepDecision decision =
        WorkflowStepDecision.builder()
            .stepOrder(currentStep.getStepOrder())
            .stepName(currentStep.getStepName())
            .approverRole(currentStep.getApproverRole())
            .decision(WorkflowDecisionType.APPROVED)
            .verificationMethod(request.verificationMethod())
            .signaturePayload(request.signatureOrTotpCode().trim())
            .reason(request.comment())
            .decidedByUserId(request.decidedByUserId())
            .decidedByUsername(request.decidedByUsername())
            .decidedAt(now)
            .build();
    instance.addDecision(decision);

    Optional<WorkflowStepDefinition> nextStep =
        resolveApplicableSteps(instance).stream()
            .filter(s -> s.getStepOrder() > currentStep.getStepOrder())
            .findFirst();

    if (nextStep.isPresent()) {
      instance.setCurrentStepOrder(nextStep.get().getStepOrder());
      return workflowMapper.toInstanceDto(instanceRepository.save(instance));
    }

    instance.setState(WorkflowState.APPROVED);
    instance.setCurrentStepOrder(null);
    instance.setCompletedAt(now);
    WorkflowInstance saved = instanceRepository.save(instance);
    publishCompletedEvent(saved, request.comment(), now);
    return workflowMapper.toInstanceDto(saved);
  }

  @Transactional
  public WorkflowInstanceDto rejectStep(UUID instanceId, StepRejectionRequest request) {
    WorkflowInstance instance = findInstanceOrThrow(instanceId);
    ensureInAgreement(instance);
    if (request == null || request.reason() == null || request.reason().isBlank()) {
      throw new IllegalArgumentException("Rejection reason must not be blank");
    }

    WorkflowStepDefinition currentStep = findCurrentStepOrThrow(instance);
    Instant now = Instant.now();

    WorkflowStepDecision decision =
        WorkflowStepDecision.builder()
            .stepOrder(currentStep.getStepOrder())
            .stepName(currentStep.getStepName())
            .approverRole(currentStep.getApproverRole())
            .decision(WorkflowDecisionType.REJECTED)
            .reason(request.reason().trim())
            .decidedByUserId(request.decidedByUserId())
            .decidedByUsername(request.decidedByUsername())
            .decidedAt(now)
            .build();
    instance.addDecision(decision);

    instance.setState(WorkflowState.REJECTED);
    instance.setRejectionReason(request.reason().trim());
    instance.setCurrentStepOrder(null);
    instance.setCompletedAt(now);

    WorkflowInstance saved = instanceRepository.save(instance);
    publishCompletedEvent(saved, saved.getRejectionReason(), now);
    return workflowMapper.toInstanceDto(saved);
  }

  @Transactional
  public WorkflowInstanceDto returnStepForRework(UUID instanceId, StepReworkRequest request) {
    WorkflowInstance instance = findInstanceOrThrow(instanceId);
    ensureInAgreement(instance);
    if (request == null || request.reason() == null || request.reason().isBlank()) {
      throw new IllegalArgumentException("Rework reason must not be blank");
    }

    WorkflowStepDefinition currentStep = findCurrentStepOrThrow(instance);
    Instant now = Instant.now();

    WorkflowStepDecision decision =
        WorkflowStepDecision.builder()
            .stepOrder(currentStep.getStepOrder())
            .stepName(currentStep.getStepName())
            .approverRole(currentStep.getApproverRole())
            .decision(WorkflowDecisionType.RETURNED_FOR_REWORK)
            .reason(request.reason().trim())
            .decidedByUserId(request.decidedByUserId())
            .decidedByUsername(request.decidedByUsername())
            .decidedAt(now)
            .build();
    instance.addDecision(decision);

    instance.setState(WorkflowState.DRAFT);
    instance.setReworkReason(request.reason().trim());
    instance.setCurrentStepOrder(null);

    WorkflowInstance saved = instanceRepository.save(instance);
    eventPublisher.publishEvent(
        new WorkflowReturnedForReworkEvent(
            saved.getId(),
            saved.getDocumentType(),
            saved.getDocumentId(),
            saved.getDocumentNumber(),
            currentStep.getStepOrder(),
            saved.getReworkReason(),
            now));
    return workflowMapper.toInstanceDto(saved);
  }

  private List<WorkflowStepDefinition> resolveApplicableSteps(WorkflowInstance instance) {
    return instance.getDefinition().getSteps().stream()
        .sorted(Comparator.comparingInt(WorkflowStepDefinition::getStepOrder))
        .filter(
            step -> step.matchesConditions(instance.getAmount(), instance.getContextAttributes()))
        .toList();
  }

  private WorkflowStepDefinition findCurrentStepOrThrow(WorkflowInstance instance) {
    Integer currentOrder = instance.getCurrentStepOrder();
    if (currentOrder == null) {
      throw new IllegalStateException("Workflow instance has no active currentStepOrder");
    }
    return instance.getDefinition().getSteps().stream()
        .filter(s -> s.getStepOrder() == currentOrder)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Step definition not found for stepOrder " + currentOrder));
  }

  private void validateVerification(WorkflowStepDefinition step, StepApprovalRequest request) {
    if (request == null || request.verificationMethod() == null) {
      throw new IllegalArgumentException("Verification method (E_IMZO or TOTP) is required");
    }
    if (!step.getRequiredVerificationMethod().isSatisfiedBy(request.verificationMethod())) {
      throw new IllegalArgumentException(
          "Step '"
              + step.getStepName()
              + "' requires verification method "
              + step.getRequiredVerificationMethod()
              + ", but got "
              + request.verificationMethod());
    }
    if (request.signatureOrTotpCode() == null || request.signatureOrTotpCode().isBlank()) {
      throw new IllegalArgumentException("E-IMZO signature or TOTP code must not be blank");
    }
    String proof = request.signatureOrTotpCode().trim();
    if (request.verificationMethod() == VerificationMethod.TOTP && !proof.matches("^\\d{6}$")) {
      throw new IllegalArgumentException("TOTP verification requires a 6-digit numeric code");
    }
  }

  private void ensureInAgreement(WorkflowInstance instance) {
    if (instance.getState() != WorkflowState.IN_AGREEMENT) {
      throw new IllegalStateException(
          "Workflow instance must be in IN_AGREEMENT state for step decisions. Current state: "
              + instance.getState());
    }
  }

  private void publishCompletedEvent(
      WorkflowInstance instance, String reason, Instant completedAt) {
    eventPublisher.publishEvent(
        new WorkflowCompletedEvent(
            instance.getId(),
            instance.getDocumentType(),
            instance.getDocumentId(),
            instance.getDocumentNumber(),
            instance.getState(),
            reason,
            completedAt));
  }

  private WorkflowInstance findInstanceOrThrow(UUID id) {
    return instanceRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("WorkflowInstance", id));
  }
}
