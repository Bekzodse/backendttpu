package uz.ttpu.inventory.movement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.ttpu.inventory.common.domain.BaseEntity;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "clearance_checklists")
public class ClearanceChecklist extends BaseEntity {

  @Column(name = "checklist_number", nullable = false, unique = true, length = 100)
  private String checklistNumber;

  @Column(name = "employee_id", nullable = false)
  private UUID employeeId;

  @Column(name = "employee_code", length = 50)
  private String employeeCode;

  @Column(name = "employee_full_name", length = 255)
  private String employeeFullName;

  @Column(name = "department_name", length = 255)
  private String departmentName;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private ClearanceChecklistStatus status = ClearanceChecklistStatus.OPEN;

  @Column(name = "dismissal_order_number", length = 100)
  private String dismissalOrderNumber;

  @Column(name = "dismissal_reason", columnDefinition = "TEXT")
  private String dismissalReason;

  @Builder.Default
  @Column(name = "total_assigned_items", nullable = false)
  private int totalAssignedItems = 0;

  @Builder.Default
  @Column(name = "remaining_items_count", nullable = false)
  private int remainingItemsCount = 0;

  @Column(name = "opened_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private Instant openedAt;

  @Column(name = "closed_at", columnDefinition = "TIMESTAMPTZ")
  private Instant closedAt;

  @Column(name = "closed_by_employee_id")
  private UUID closedByEmployeeId;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;
}
