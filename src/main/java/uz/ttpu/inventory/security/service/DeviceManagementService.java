package uz.ttpu.inventory.security.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.common.domain.DeviceRevokedException;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.security.api.UserDeviceDto;
import uz.ttpu.inventory.security.domain.UserAccount;
import uz.ttpu.inventory.security.domain.UserDevice;
import uz.ttpu.inventory.security.repository.RefreshTokenRepository;
import uz.ttpu.inventory.security.repository.UserDeviceRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceManagementService {

  private final UserDeviceRepository userDeviceRepository;
  private final RefreshTokenRepository refreshTokenRepository;

  // In-memory cache of revoked device IDs updated on revocation and refreshed every 60 seconds
  private final Set<String> revokedDeviceCache = ConcurrentHashMap.newKeySet();

  @Transactional
  public UserDevice registerOrUpdateDevice(
      UserAccount user,
      String deviceId,
      String deviceName,
      String deviceType,
      String osVersion,
      String appVersion,
      String ipAddress,
      String userAgent) {
    if (deviceId == null || deviceId.isBlank()) {
      return null;
    }
    String normalizedDeviceId = deviceId.trim();

    if (isDeviceRevoked(normalizedDeviceId)) {
      throw new DeviceRevokedException(normalizedDeviceId);
    }

    Instant now = Instant.now();
    Optional<UserDevice> existingOpt =
        userDeviceRepository.findByUserIdAndDeviceId(user.getId(), normalizedDeviceId);

    UserDevice device;
    if (existingOpt.isPresent()) {
      device = existingOpt.get();
      if (device.isRevoked()) {
        revokedDeviceCache.add(normalizedDeviceId);
        throw new DeviceRevokedException(normalizedDeviceId);
      }
      device.setDeviceName(deviceName != null ? deviceName : device.getDeviceName());
      device.setDeviceType(deviceType != null ? deviceType : device.getDeviceType());
      device.setOsVersion(osVersion != null ? osVersion : device.getOsVersion());
      device.setAppVersion(appVersion != null ? appVersion : device.getAppVersion());
      device.setIpAddress(ipAddress);
      device.setUserAgent(userAgent);
      device.setLastLoginAt(now);
    } else {
      device =
          UserDevice.builder()
              .user(user)
              .deviceId(normalizedDeviceId)
              .deviceName(deviceName != null ? deviceName : "Mobile Device")
              .deviceType(deviceType != null ? deviceType : "MOBILE")
              .osVersion(osVersion)
              .appVersion(appVersion)
              .ipAddress(ipAddress)
              .userAgent(userAgent)
              .revoked(false)
              .lastLoginAt(now)
              .build();
    }

    return userDeviceRepository.save(device);
  }

  @Transactional
  public UserDeviceDto revokeDevice(String deviceId, String reason) {
    if (deviceId == null || deviceId.isBlank()) {
      throw new IllegalArgumentException("Device ID cannot be empty");
    }
    String normalized = deviceId.trim();
    UserDevice device =
        userDeviceRepository
            .findByDeviceId(normalized)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "UserDevice not found for deviceId: " + normalized));

    device.setRevoked(true);
    device.setRevokedAt(Instant.now());
    device.setRevocationReason(reason != null ? reason : "Revoked by administrator");
    UserDevice saved = userDeviceRepository.save(device);

    revokedDeviceCache.add(normalized);
    log.warn(
        "Device {} for user {} was REVOKED. Invalidation active within 1 minute.",
        normalized,
        device.getUser().getUsername());

    return toDto(saved);
  }

  @Transactional(readOnly = true)
  public boolean isDeviceRevoked(String deviceId) {
    if (deviceId == null || deviceId.isBlank()) {
      return false;
    }
    String normalized = deviceId.trim();
    if (revokedDeviceCache.contains(normalized)) {
      return true;
    }
    boolean revokedInDb =
        userDeviceRepository.findByDeviceId(normalized).map(UserDevice::isRevoked).orElse(false);
    if (revokedInDb) {
      revokedDeviceCache.add(normalized);
    }
    return revokedInDb;
  }

  @Scheduled(fixedRate = 60000)
  @Transactional(readOnly = true)
  public void refreshRevokedDeviceCache() {
    try {
      List<UserDevice> revokedDevices = userDeviceRepository.findAllByRevokedTrue();
      Set<String> fresh = ConcurrentHashMap.newKeySet();
      for (UserDevice dev : revokedDevices) {
        if (dev.getDeviceId() != null) {
          fresh.add(dev.getDeviceId().trim());
        }
      }
      revokedDeviceCache.clear();
      revokedDeviceCache.addAll(fresh);
    } catch (Exception e) {
      log.warn("Failed to refresh revoked device cache: {}", e.getMessage());
    }
  }

  @Transactional(readOnly = true)
  public List<UserDeviceDto> listDevicesForUser(UUID userId) {
    return userDeviceRepository.findAllByUserIdOrderByLastLoginAtDesc(userId).stream()
        .map(this::toDto)
        .toList();
  }

  private UserDeviceDto toDto(UserDevice d) {
    return new UserDeviceDto(
        d.getId(),
        d.getUser().getId(),
        d.getUser().getUsername(),
        d.getDeviceId(),
        d.getDeviceName(),
        d.getDeviceType(),
        d.getOsVersion(),
        d.getAppVersion(),
        d.getIpAddress(),
        d.isRevoked(),
        d.getRevokedAt(),
        d.getRevocationReason(),
        d.getLastLoginAt(),
        d.getCreatedAt());
  }
}
