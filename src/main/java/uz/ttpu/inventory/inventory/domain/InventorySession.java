package uz.ttpu.inventory.inventory.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.ttpu.inventory.common.domain.BaseEntity;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "inventory_sessions")
public class InventorySession extends BaseEntity {

  @Column(name = "session_code", nullable = false, unique = true, length = 100)
  private String sessionCode;

  @Column(name = "rector_order_number", nullable = false, length = 150)
  private String rectorOrderNumber;

  @Column(name = "rector_order_date", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private Instant rectorOrderDate;

  @Column(name = "session_date", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private Instant sessionDate;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private InventorySessionStatus status = InventorySessionStatus.DRAFT;

  @Column(name = "chairman_employee_id", nullable = false)
  private UUID chairmanEmployeeId;

  @Builder.Default
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "commission_member_ids", nullable = false, columnDefinition = "jsonb")
  private List<UUID> commissionMemberIds = new ArrayList<>();

  @Builder.Default
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "room_ids", nullable = false, columnDefinition = "jsonb")
  private List<UUID> roomIds = new ArrayList<>();

  @Column(name = "started_at", columnDefinition = "TIMESTAMPTZ")
  private Instant startedAt;

  @Column(name = "completed_at", columnDefinition = "TIMESTAMPTZ")
  private Instant completedAt;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Builder.Default
  @Column(name = "package_version", nullable = false)
  private long packageVersion = 1L;

  @Builder.Default
  @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("createdAt ASC")
  private List<InventorySessionRoom> sessionRooms = new ArrayList<>();

  @Builder.Default
  @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("inventoryNumber ASC")
  private List<InventoryExpectedItem> expectedItems = new ArrayList<>();

  public void addSessionRoom(InventorySessionRoom room) {
    room.setSession(this);
    this.sessionRooms.add(room);
  }

  public void addExpectedItem(InventoryExpectedItem expectedItem) {
    expectedItem.setSession(this);
    this.expectedItems.add(expectedItem);
  }

  public void incrementPackageVersion() {
    this.packageVersion = Math.max(1L, this.packageVersion + 1L);
  }
}
