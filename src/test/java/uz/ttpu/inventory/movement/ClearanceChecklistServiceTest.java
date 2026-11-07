package uz.ttpu.inventory.movement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.common.domain.ClearanceChecklistBlockedException;
import uz.ttpu.inventory.integration.api.EmployeeDismissedEvent;
import uz.ttpu.inventory.movement.api.ClearanceChecklistDto;
import uz.ttpu.inventory.movement.api.CloseClearanceChecklistRequest;
import uz.ttpu.inventory.movement.api.OpenClearanceChecklistRequest;
import uz.ttpu.inventory.movement.domain.ClearanceChecklist;
import uz.ttpu.inventory.movement.domain.ClearanceChecklistStatus;
import uz.ttpu.inventory.movement.repository.ClearanceChecklistRepository;
import uz.ttpu.inventory.movement.service.ClearanceChecklistService;
import uz.ttpu.inventory.references.api.EmployeeDto;
import uz.ttpu.inventory.references.api.ReferenceSyncApi;

class ClearanceChecklistServiceTest {

  private ClearanceChecklistRepository checklistRepository;
  private AssetLogisticsApi assetLogisticsApi;
  private ReferenceSyncApi referenceSyncApi;
  private ApplicationEventPublisher eventPublisher;
  private ClearanceChecklistService checklistService;

  private final List<ClearanceChecklist> inMemoryDb = new ArrayList<>();

  @BeforeEach
  void setUp() {
    checklistRepository = mock(ClearanceChecklistRepository.class);
    assetLogisticsApi = mock(AssetLogisticsApi.class);
    referenceSyncApi = mock(ReferenceSyncApi.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    inMemoryDb.clear();

    when(checklistRepository.save(any(ClearanceChecklist.class)))
        .thenAnswer(
            inv -> {
              ClearanceChecklist c = inv.getArgument(0);
              if (c.getId() == null) {
                c.setId(UUID.randomUUID());
              }
              if (c.getCreatedAt() == null) {
                c.setCreatedAt(Instant.now());
              }
              inMemoryDb.removeIf(existing -> existing.getId().equals(c.getId()));
              inMemoryDb.add(c);
              return c;
            });

    when(checklistRepository.findById(any(UUID.class)))
        .thenAnswer(
            inv -> {
              UUID id = inv.getArgument(0);
              return inMemoryDb.stream().filter(c -> c.getId().equals(id)).findFirst();
            });

    when(checklistRepository.findFirstByEmployeeIdAndStatus(
            any(UUID.class), any(ClearanceChecklistStatus.class)))
        .thenAnswer(
            inv -> {
              UUID empId = inv.getArgument(0);
              ClearanceChecklistStatus status = inv.getArgument(1);
              return inMemoryDb.stream()
                  .filter(c -> c.getEmployeeId().equals(empId) && c.getStatus() == status)
                  .findFirst();
            });

    checklistService =
        new ClearanceChecklistService(
            checklistRepository, assetLogisticsApi, referenceSyncApi, eventPublisher);
  }

  private ItemDto createItemDto(UUID id, String invNum, String name, UUID custodianId) {
    return new ItemDto(
        id,
        invNum,
        "SN-" + invNum,
        "QR-" + invNum,
        name,
        "Description",
        UUID.randomUUID(),
        uz.ttpu.inventory.asset.domain.ItemState.FOYDALANISHDA,
        BigDecimal.valueOf(15000000),
        BigDecimal.valueOf(10000000),
        false,
        Instant.now(),
        Instant.now().plusSeconds(86400 * 365),
        null,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        custodianId,
        java.util.Map.of(),
        "1C-" + invNum,
        Instant.now(),
        Instant.now());
  }

  @Test
  @DisplayName(
      "B3.3: Opening a clearance checklist for an employee assigns a unique number and records"
          + " items")
  void testOpenClearanceChecklist() {
    UUID employeeId = UUID.randomUUID();
    when(referenceSyncApi.findEmployeeByCodeOrFullName(null, null))
        .thenReturn(
            Optional.of(
                new EmployeeDto(
                    employeeId,
                    "EMP-001",
                    "Alisher",
                    "Navoiy",
                    "",
                    "alisher@ttpu.uz",
                    "+998901234567",
                    "Engineer",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "HEMIS-001",
                    "1C-001",
                    true,
                    Instant.now(),
                    Instant.now())));

    ItemDto laptop = createItemDto(UUID.randomUUID(), "INV-1001", "ThinkPad X1", employeeId);

    when(assetLogisticsApi.findItemsByResponsibleEmployee(employeeId)).thenReturn(List.of(laptop));

    ClearanceChecklistDto dto =
        checklistService.openChecklist(
            new OpenClearanceChecklistRequest(
                employeeId, "HEMIS-2026-99", "Dismissal by HEMIS decree", "Offboarding initiated"));

    assertThat(dto).isNotNull();
    assertThat(dto.employeeId()).isEqualTo(employeeId);
    assertThat(dto.status()).isEqualTo(ClearanceChecklistStatus.OPEN);
    assertThat(dto.dismissalOrderNumber()).isEqualTo("HEMIS-2026-99");
    assertThat(dto.totalAssignedItems()).isEqualTo(1);
    assertThat(dto.remainingItemsCount()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "B3.3: Attempting to close clearance checklist while employee still holds items throws"
          + " ClearanceChecklistBlockedException")
  void testCloseChecklistBlockedWhenItemsRemain() {
    UUID employeeId = UUID.randomUUID();
    ClearanceChecklist checklist =
        ClearanceChecklist.builder()
            .checklistNumber("CHK-2026-0001")
            .employeeId(employeeId)
            .employeeFullName("Alisher Navoiy")
            .status(ClearanceChecklistStatus.OPEN)
            .totalAssignedItems(1)
            .openedAt(Instant.now())
            .build();
    checklist.setId(UUID.randomUUID());
    checklist.setCreatedAt(Instant.now());
    inMemoryDb.add(checklist);

    ItemDto laptop = createItemDto(UUID.randomUUID(), "INV-1001", "ThinkPad X1", employeeId);

    when(assetLogisticsApi.findItemsByResponsibleEmployee(employeeId)).thenReturn(List.of(laptop));

    UUID adminId = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                checklistService.closeChecklist(
                    checklist.getId(),
                    new CloseClearanceChecklistRequest(adminId, "Attempting closure")))
        .isInstanceOf(ClearanceChecklistBlockedException.class)
        .hasMessageContaining("cannot be closed");

    assertThat(checklist.getStatus()).isEqualTo(ClearanceChecklistStatus.OPEN);
  }

  @Test
  @DisplayName(
      "B3.3: Closing clearance checklist succeeds when all items are returned/transferred (0"
          + " pending items)")
  void testCloseChecklistSucceedsWhenAllItemsReturned() {
    UUID employeeId = UUID.randomUUID();
    ClearanceChecklist checklist =
        ClearanceChecklist.builder()
            .checklistNumber("CHK-2026-0002")
            .employeeId(employeeId)
            .employeeFullName("Alisher Navoiy")
            .status(ClearanceChecklistStatus.OPEN)
            .totalAssignedItems(1)
            .openedAt(Instant.now())
            .build();
    checklist.setId(UUID.randomUUID());
    checklist.setCreatedAt(Instant.now());
    inMemoryDb.add(checklist);

    // Employee returned everything
    when(assetLogisticsApi.findItemsByResponsibleEmployee(employeeId)).thenReturn(List.of());

    UUID adminId = UUID.randomUUID();
    ClearanceChecklistDto closed =
        checklistService.closeChecklist(
            checklist.getId(),
            new CloseClearanceChecklistRequest(adminId, "All equipment handed over"));

    assertThat(closed.status()).isEqualTo(ClearanceChecklistStatus.CLOSED);
    assertThat(closed.closedByEmployeeId()).isEqualTo(adminId);
    assertThat(closed.closedAt()).isNotNull();
  }

  @Test
  @DisplayName("B3.3: EmployeeDismissedEvent automatically opens a clearance checklist")
  void testEmployeeDismissedEventOpensChecklist() {
    UUID employeeId = UUID.randomUUID();
    when(referenceSyncApi.findEmployeeByCodeOrFullName(null, null))
        .thenReturn(
            Optional.of(
                new EmployeeDto(
                    employeeId,
                    "EMP-999",
                    "Bobur",
                    "Mirzo",
                    "",
                    "bobur@ttpu.uz",
                    "+998909876543",
                    "Docent",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "HEMIS-999",
                    "1C-999",
                    true,
                    Instant.now(),
                    Instant.now())));
    when(assetLogisticsApi.findItemsByResponsibleEmployee(employeeId)).thenReturn(List.of());

    checklistService.onEmployeeDismissed(
        new EmployeeDismissedEvent(employeeId, "ORDER-2026-888", "Contract ended", Instant.now()));

    assertThat(inMemoryDb).hasSize(1);
    ClearanceChecklist created = inMemoryDb.get(0);
    assertThat(created.getEmployeeId()).isEqualTo(employeeId);
    assertThat(created.getStatus()).isEqualTo(ClearanceChecklistStatus.OPEN);
    assertThat(created.getDismissalOrderNumber()).isEqualTo("ORDER-2026-888");
  }
}
