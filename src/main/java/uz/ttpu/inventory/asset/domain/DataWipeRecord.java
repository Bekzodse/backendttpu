package uz.ttpu.inventory.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "data_wipe_records")
public class DataWipeRecord extends BaseEntity {

  @Column(name = "item_id", nullable = false)
  private UUID itemId;

  @Column(name = "performed_by_name", nullable = false)
  private String performedByName;

  @Column(name = "performed_by_employee_id")
  private UUID performedByEmployeeId;

  @Column(name = "wipe_method", nullable = false, length = 100)
  private String wipeMethod;

  @Column(name = "certificate_number", length = 100)
  private String certificateNumber;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Column(name = "wiped_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private Instant wipedAt;
}
