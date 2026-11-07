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
import org.hibernate.annotations.Immutable;
import uz.ttpu.inventory.common.domain.BaseEntity;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Immutable
@Entity
@Table(name = "movements")
public class MovementRecord extends BaseEntity {

  @Column(name = "operation_id", nullable = false, updatable = false)
  private UUID operationId;

  @Column(name = "document_number", nullable = false, updatable = false, length = 100)
  private String documentNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "movement_type", nullable = false, updatable = false, length = 50)
  private MovementType movementType;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, updatable = false, length = 50)
  private MovementEventType eventType;

  @Column(name = "item_id", nullable = false, updatable = false)
  private UUID itemId;

  @Column(name = "from_building_id", updatable = false)
  private UUID fromBuildingId;

  @Column(name = "to_building_id", updatable = false)
  private UUID toBuildingId;

  @Column(name = "from_floor_id", updatable = false)
  private UUID fromFloorId;

  @Column(name = "to_floor_id", updatable = false)
  private UUID toFloorId;

  @Column(name = "from_room_id", updatable = false)
  private UUID fromRoomId;

  @Column(name = "to_room_id", nullable = false, updatable = false)
  private UUID toRoomId;

  @Column(name = "from_department_id", updatable = false)
  private UUID fromDepartmentId;

  @Column(name = "to_department_id", updatable = false)
  private UUID toDepartmentId;

  @Column(name = "from_employee_id", updatable = false)
  private UUID fromEmployeeId;

  @Column(name = "to_employee_id", nullable = false, updatable = false)
  private UUID toEmployeeId;

  @Column(name = "actor_employee_id", updatable = false)
  private UUID actorEmployeeId;

  @Column(name = "totp_verified", nullable = false, updatable = false)
  private boolean totpVerified;

  @Column(name = "reason", updatable = false, columnDefinition = "TEXT")
  private String reason;

  @Column(
      name = "recorded_at",
      nullable = false,
      updatable = false,
      columnDefinition = "TIMESTAMPTZ")
  private Instant recordedAt;
}
