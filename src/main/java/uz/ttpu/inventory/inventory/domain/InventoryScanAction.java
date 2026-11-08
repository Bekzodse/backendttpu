package uz.ttpu.inventory.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    name = "inventory_scan_actions",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_inventory_scan_actions_client_action_id",
          columnNames = {"client_action_id"})
    })
public class InventoryScanAction extends BaseEntity {

  @Column(name = "client_action_id", nullable = false, unique = true)
  private UUID clientActionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_id", nullable = false)
  private InventorySession session;

  @Column(name = "scanned_room_id", nullable = false)
  private UUID scannedRoomId;

  @Column(name = "scanned_by_employee_id", nullable = false)
  private UUID scannedByEmployeeId;

  @Column(name = "expected_room_id")
  private UUID expectedRoomId;

  @Column(name = "item_id")
  private UUID itemId;

  @Column(name = "inventory_number", length = 100)
  private String inventoryNumber;

  @Column(name = "qr_code", length = 100)
  private String qrCode;

  @Column(name = "item_name", length = 255)
  private String itemName;

  @Column(name = "scanned_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private Instant scannedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "reconciliation_category", nullable = false, length = 64)
  private ReconciliationCategory reconciliationCategory;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Builder.Default
  @Column(name = "conflict", nullable = false)
  private boolean conflict = false;

  @Column(name = "conflict_details", columnDefinition = "TEXT")
  private String conflictDetails;
}
