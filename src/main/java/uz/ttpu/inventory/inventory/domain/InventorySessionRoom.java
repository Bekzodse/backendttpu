package uz.ttpu.inventory.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
    name = "inventory_session_rooms",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_inventory_session_rooms_session_room",
          columnNames = {"session_id", "room_id"})
    })
public class InventorySessionRoom extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_id", nullable = false)
  private InventorySession session;

  @Column(name = "room_id", nullable = false)
  private UUID roomId;

  @Builder.Default
  @Column(name = "expected_count", nullable = false)
  private int expectedCount = 0;

  @Builder.Default
  @Column(name = "found_count", nullable = false)
  private int foundCount = 0;

  @Builder.Default
  @Column(name = "not_found_count", nullable = false)
  private int notFoundCount = 0;

  @Builder.Default
  @Column(name = "completed", nullable = false)
  private boolean completed = false;

  @Column(name = "completed_at", columnDefinition = "TIMESTAMPTZ")
  private Instant completedAt;
}
