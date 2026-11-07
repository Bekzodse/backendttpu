package uz.ttpu.inventory.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.ttpu.inventory.common.domain.AuditActionType;
import uz.ttpu.inventory.common.domain.BaseEntity;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "audit_record")
public class AuditRecord extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "action_type", nullable = false, length = 32)
  private AuditActionType actionType;

  @Column(name = "target_type", nullable = false, length = 64)
  private String targetType;

  @Column(name = "target_id", length = 255)
  private String targetId;

  @Column(name = "method_name", nullable = false, length = 150)
  private String methodName;

  @Column(name = "actor_username", nullable = false, length = 150)
  private String actorUsername;

  @Column(name = "details", columnDefinition = "TEXT")
  private String details;

  @Column(name = "occurred_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private Instant occurredAt;
}
