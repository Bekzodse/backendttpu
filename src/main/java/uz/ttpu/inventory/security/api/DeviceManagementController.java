package uz.ttpu.inventory.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.security.service.DeviceManagementService;

@RestController
@RequestMapping("/api/v1/security/devices")
@RequiredArgsConstructor
@Tag(
    name = "01. Mobile Device Management & Revocation (NFT-3.8)",
    description =
        "Registers mobile devices upon login and provides administrator endpoints to revoke"
            + " devices, making their JWTs invalid within 1 minute.")
public class DeviceManagementController {

  private final DeviceManagementService deviceManagementService;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "List registered devices for the current user",
      description = "Returns all devices registered by the authenticated user.")
  public ResponseEntity<List<UserDeviceDto>> listMyDevices(@AuthenticationPrincipal Jwt jwt) {
    java.util.UUID userId = java.util.UUID.fromString(jwt.getSubject());
    return ResponseEntity.ok(deviceManagementService.listDevicesForUser(userId));
  }

  @PostMapping("/{deviceId}/revoke")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Revoke a mobile device (NFT-3.8)",
      description =
          "Revokes a registered device ID. Any active JWT access token associated with this device"
              + " becomes invalid within 1 minute.")
  public ResponseEntity<UserDeviceDto> revokeDevice(
      @PathVariable String deviceId, @RequestBody(required = false) RevokeDeviceRequest request) {
    String reason = request != null ? request.reason() : "Revoked by admin";
    return ResponseEntity.ok(deviceManagementService.revokeDevice(deviceId, reason));
  }
}
