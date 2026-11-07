package uz.ttpu.inventory.asset.domain;

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
@Table(name = "item_state_history")
public class ItemStateHistory extends BaseEntity {

  @Column(name = "item_id", nullable = false)
  private UUID itemId;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_state", nullable = false, length = 50)
  private ItemState fromState;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_state", nullable = false, length = 50)
  private ItemState toState;

  @Enumerated(EnumType.STRING)
  @Column(name = "event", nullable = false, length = 50)
  private ItemEvent event;

  @Column(name = "document_type", nullable = false, length = 100)
  private String documentType;

  @Column(name = "document_ref", nullable = false, length = 255)
  private String documentRef;

  @Column(name = "transitioned_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private Instant transitionedAt;
}
