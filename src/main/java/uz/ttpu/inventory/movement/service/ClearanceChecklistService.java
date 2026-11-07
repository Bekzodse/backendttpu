package uz.ttpu.inventory.movement.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.common.domain.ClearanceChecklistBlockedException;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.integration.api.EmployeeDismissedEvent;
import uz.ttpu.inventory.movement.api.ClearanceChecklistApi;
import uz.ttpu.inventory.movement.api.ClearanceChecklistClosedEvent;
import uz.ttpu.inventory.movement.api.ClearanceChecklistDto;
import uz.ttpu.inventory.movement.api.ClearanceChecklistOpenedEvent;
import uz.ttpu.inventory.movement.api.CloseClearanceChecklistRequest;
import uz.ttpu.inventory.movement.api.OpenClearanceChecklistRequest;
import uz.ttpu.inventory.movement.domain.ClearanceChecklist;
import uz.ttpu.inventory.movement.domain.ClearanceChecklistStatus;
import uz.ttpu.inventory.movement.repository.ClearanceChecklistRepository;
import uz.ttpu.inventory.references.api.EmployeeDto;
import uz.ttpu.inventory.references.api.ReferenceSyncApi;

@Service
@RequiredArgsConstructor
public class ClearanceChecklistService implements ClearanceChecklistApi {

  private final ClearanceChecklistRepository checklistRepository;
  private final AssetLogisticsApi assetLogisticsApi;
  private final ReferenceSyncApi referenceSyncApi;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional
  public ClearanceChecklistDto openChecklist(OpenClearanceChecklistRequest request) {
    UUID employeeId = request.employeeId();
    Optional<ClearanceChecklist> existing =
        checklistRepository.findFirstByEmployeeIdAndStatus(
            employeeId, ClearanceChecklistStatus.OPEN);
    if (existing.isPresent()) {
      return toDto(existing.get());
    }

    EmployeeDto employee =
        referenceSyncApi
            .findEmployeeByCodeOrFullName(null, null)
            .filter(e -> e.id().equals(employeeId))
            .orElse(null);

    String empCode =
        employee != null ? employee.employeeCode() : "EMP-" + employeeId.toString().substring(0, 8);
    String empName =
        employee != null
            ? (employee.firstName() + " " + employee.lastName()).trim()
            : "Employee " + employeeId;
    String deptName = employee != null ? employee.position() : null;

    List<ItemDto> assignedItems = assetLogisticsApi.findItemsByResponsibleEmployee(employeeId);
    int totalItems = assignedItems.size();

    String checklistNumber = "AV-" + Instant.now().toEpochMilli();

    ClearanceChecklist checklist =
        ClearanceChecklist.builder()
            .checklistNumber(checklistNumber)
            .employeeId(employeeId)
            .employeeCode(empCode)
            .employeeFullName(empName)
            .departmentName(deptName)
            .status(ClearanceChecklistStatus.OPEN)
            .dismissalOrderNumber(request.dismissalOrderNumber())
            .dismissalReason(request.dismissalReason())
            .totalAssignedItems(totalItems)
            .remainingItemsCount(totalItems)
            .openedAt(Instant.now())
            .notes(request.notes())
            .build();

    ClearanceChecklist saved = checklistRepository.save(checklist);

    eventPublisher.publishEvent(
        new ClearanceChecklistOpenedEvent(
            saved.getId(),
            saved.getChecklistNumber(),
            saved.getEmployeeId(),
            saved.getEmployeeFullName(),
            saved.getTotalAssignedItems(),
            saved.getOpenedAt()));

    return toDto(saved);
  }

  @Override
  @Transactional
  public ClearanceChecklistDto closeChecklist(
      UUID checklistId, CloseClearanceChecklistRequest request) {
    ClearanceChecklist checklist = findOrThrow(checklistId);

    if (checklist.getStatus() == ClearanceChecklistStatus.CLOSED) {
      return toDto(checklist);
    }

    List<ItemDto> remainingItems =
        assetLogisticsApi.findItemsByResponsibleEmployee(checklist.getEmployeeId());
    if (!remainingItems.isEmpty()) {
      List<String> summaries =
          remainingItems.stream().map(i -> i.inventoryNumber() + " (" + i.name() + ")").toList();
      checklist.setRemainingItemsCount(remainingItems.size());
      checklistRepository.save(checklist);
      throw new ClearanceChecklistBlockedException(
          checklist.getChecklistNumber(), remainingItems.size(), summaries);
    }

    Instant now = Instant.now();
    checklist.setStatus(ClearanceChecklistStatus.CLOSED);
    checklist.setRemainingItemsCount(0);
    checklist.setClosedAt(now);
    if (request != null) {
      checklist.setClosedByEmployeeId(request.closedByEmployeeId());
      if (request.notes() != null && !request.notes().isBlank()) {
        checklist.setNotes(request.notes());
      }
    }

    ClearanceChecklist saved = checklistRepository.save(checklist);

    eventPublisher.publishEvent(
        new ClearanceChecklistClosedEvent(
            saved.getId(),
            saved.getChecklistNumber(),
            saved.getEmployeeId(),
            saved.getEmployeeFullName(),
            now));

    return toDto(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public ClearanceChecklistDto getChecklist(UUID checklistId) {
    return toDto(findOrThrow(checklistId));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ClearanceChecklistDto> getActiveChecklistForEmployee(UUID employeeId) {
    return checklistRepository
        .findFirstByEmployeeIdAndStatus(employeeId, ClearanceChecklistStatus.OPEN)
        .map(this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ClearanceChecklistDto> listChecklists() {
    return checklistRepository.findAll().stream().map(this::toDto).toList();
  }

  private ClearanceChecklistDto toDto(ClearanceChecklist c) {
    List<ItemDto> currentItems =
        assetLogisticsApi.findItemsByResponsibleEmployee(c.getEmployeeId());
    List<String> summaries =
        currentItems.stream().map(i -> i.inventoryNumber() + " (" + i.name() + ")").toList();

    return new ClearanceChecklistDto(
        c.getId(),
        c.getChecklistNumber(),
        c.getEmployeeId(),
        c.getEmployeeCode(),
        c.getEmployeeFullName(),
        c.getDepartmentName(),
        c.getStatus(),
        c.getDismissalOrderNumber(),
        c.getDismissalReason(),
        c.getTotalAssignedItems(),
        currentItems.size(),
        summaries,
        c.getOpenedAt(),
        c.getClosedAt(),
        c.getClosedByEmployeeId(),
        c.getNotes(),
        c.getCreatedAt());
  }

  @org.springframework.context.event.EventListener
  @Transactional
  public void onEmployeeDismissed(EmployeeDismissedEvent event) {
    if (event == null || event.employeeId() == null) {
      return;
    }
    openChecklist(
        new OpenClearanceChecklistRequest(
            event.employeeId(),
            event.orderNumber(),
            event.reason(),
            "Automated HEMIS dismissal trigger"));
  }

  private ClearanceChecklist findOrThrow(UUID id) {
    return checklistRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("ClearanceChecklist", id));
  }
}
