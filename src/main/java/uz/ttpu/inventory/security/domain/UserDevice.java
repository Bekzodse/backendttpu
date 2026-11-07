package uz.ttpu.inventory.security.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
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
@Table(name = "user_devices")
public class UserDevice extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserAccount user;

  @Column(name = "device_id", nullable = false, length = 120)
  private String deviceId;

  @Column(name = "device_name", length = 255)
  private String deviceName;

  @Column(name = "device_type", length = 50)
  private String deviceType;

  @Column(name = "os_version", length = 50)
  private String osVersion;

  @Column(name = "app_version", length = 50)
  private String appVersion;

  @Column(name = "ip_address", length = 100)
  private String ipAddress;

  @Column(name = "user_agent", length = 255)
  private String userAgent;

  @Builder.Default
  @Column(name = "revoked", nullable = false)
  private boolean revoked = false;

  @Column(name = "revoked_at", columnDefinition = "TIMESTAMPTZ")
  private Instant revokedAt;

  @Column(name = "revocation_reason", columnDefinition = "TEXT")
  private String revocationReason;

  @Column(name = "last_login_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private Instant lastLoginAt;
}
