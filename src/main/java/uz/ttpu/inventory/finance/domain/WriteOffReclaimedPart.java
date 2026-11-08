package uz.ttpu.inventory.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
@Table(name = "write_off_reclaimed_parts")
public class WriteOffReclaimedPart extends BaseEntity {

  @Column(name = "written_off_item_id", nullable = false)
  private UUID writtenOffItemId;

  @Column(name = "reclaimed_item_id", nullable = false)
  private UUID reclaimedItemId;

  @Column(name = "part_name", nullable = false, length = 255)
  private String partName;

  @Column(name = "serial_number", length = 150)
  private String serialNumber;

  @Column(name = "inventory_number", nullable = false, length = 100)
  private String inventoryNumber;

  @Builder.Default
  @Column(name = "estimated_value", nullable = false, precision = 18, scale = 2)
  private BigDecimal estimatedValue = BigDecimal.ZERO;

  @Column(name = "warehouse_building_id")
  private UUID warehouseBuildingId;

  @Column(name = "warehouse_room_id")
  private UUID warehouseRoomId;

  @Column(name = "reclaimed_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private Instant reclaimedAt;

  @Column(name = "reclaimed_by_username", length = 100)
  private String reclaimedByUsername;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;
}
