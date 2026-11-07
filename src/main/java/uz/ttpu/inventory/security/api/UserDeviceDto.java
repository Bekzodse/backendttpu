package uz.ttpu.inventory.security.api;

import java.time.Instant;
import java.util.UUID;

public record UserDeviceDto(
    UUID id,
    UUID userId,
    String username,
    String deviceId,
    String deviceName,
    String deviceType,
    String osVersion,
    String appVersion,
    String ipAddress,
    boolean revoked,
    Instant revokedAt,
    String revocationReason,
    Instant lastLoginAt,
    Instant createdAt) {}
