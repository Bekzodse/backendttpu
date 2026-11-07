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
@Table(name = "off_campus_permits")
public class OffCampusPermit extends BaseEntity {

  @Column(name = "permit_number", nullable = false, unique = true, length = 100)
  private String permitNumber;

  @Column(name = "item_id", nullable = false)
  private UUID itemId;

  @Column(name = "qr_code", nullable = false, length = 255)
  private String qrCode;

  @Column(name = "inventory_number", nullable = false, length = 100)
  private String inventoryNumber;

  @Column(name = "item_name", nullable = false, length = 255)
  private String itemName;

  @Column(name = "employee_id", nullable = false)
  private UUID employeeId;

  @Column(name = "employee_full_name", nullable = false, length = 255)
  private String employeeFullName;

  @Column(name = "purpose", nullable = false, columnDefinition = "TEXT")
  private String purpose;

  @Column(name = "valid_from", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private Instant validFrom;

  @Column(name = "valid_until", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private Instant validUntil;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private PermitStatus status = PermitStatus.ACTIVE;

  @Column(name = "approved_by", length = 255)
  private String approvedBy;

  @Column(name = "revoked_at", columnDefinition = "TIMESTAMPTZ")
  private Instant revokedAt;

  @Column(name = "revocation_reason", columnDefinition = "TEXT")
  private String revocationReason;

  public boolean isCurrentlyValid(Instant now) {
    if (status != PermitStatus.ACTIVE) {
      return false;
    }
    Instant current = now != null ? now : Instant.now();
    return !current.isBefore(validFrom) && !current.isAfter(validUntil);
  }
}
