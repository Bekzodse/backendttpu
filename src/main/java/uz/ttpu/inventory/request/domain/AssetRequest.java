package uz.ttpu.inventory.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "asset_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetRequest extends BaseEntity {

  @Column(name = "request_number", nullable = false, unique = true, length = 100)
  private String requestNumber;

  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @Column(name = "requested_item_name", nullable = false, length = 255)
  private String requestedItemName;

  @Column(name = "quantity", nullable = false)
  private int quantity;

  @Column(name = "estimated_unit_cost", nullable = false, precision = 18, scale = 2)
  private BigDecimal estimatedUnitCost;

  @Column(name = "total_estimated_cost", nullable = false, precision = 18, scale = 2)
  private BigDecimal totalEstimatedCost;

  @Column(name = "department_id", nullable = false)
  private UUID departmentId;

  @Column(name = "building_id")
  private UUID buildingId;

  @Column(name = "requester_employee_id", nullable = false)
  private UUID requesterEmployeeId;

  @Column(name = "justification", length = 1000)
  private String justification;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 40)
  @Builder.Default
  private AssetRequestStatus status = AssetRequestStatus.DRAFT;

  @Column(name = "fulfilled_item_id")
  private UUID fulfilledItemId;

  @Column(name = "submitted_at", columnDefinition = "TIMESTAMPTZ")
  private Instant submittedAt;

  @Column(name = "resolved_at", columnDefinition = "TIMESTAMPTZ")
  private Instant resolvedAt;

  @Column(name = "resolved_by_username", length = 150)
  private String resolvedByUsername;
}
