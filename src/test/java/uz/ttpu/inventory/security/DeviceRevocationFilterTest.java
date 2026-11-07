package uz.ttpu.inventory.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import uz.ttpu.inventory.security.api.UserDeviceDto;
import uz.ttpu.inventory.security.domain.UserAccount;
import uz.ttpu.inventory.security.domain.UserDevice;
import uz.ttpu.inventory.security.repository.UserDeviceRepository;
import uz.ttpu.inventory.security.service.DeviceManagementService;
import uz.ttpu.inventory.security.service.DeviceRevocationFilter;

class DeviceRevocationFilterTest {

  private final List<UserDevice> deviceDb = new ArrayList<>();
  private UserDeviceRepository deviceRepository;
  private DeviceManagementService deviceService;
  private DeviceRevocationFilter filter;

  @BeforeEach
  void setUp() {
    deviceDb.clear();
    SecurityContextHolder.clearContext();

    deviceRepository = mock(UserDeviceRepository.class);
    when(deviceRepository.save(any(UserDevice.class)))
        .thenAnswer(
            inv -> {
              UserDevice d = inv.getArgument(0);
              if (d.getId() == null) {
                d.setId(UUID.randomUUID());
              }
              if (d.getCreatedAt() == null) {
                d.setCreatedAt(Instant.now());
              }
              deviceDb.removeIf(existing -> existing.getId().equals(d.getId()));
              deviceDb.add(d);
              return d;
            });

    when(deviceRepository.findByDeviceId(any(String.class)))
        .thenAnswer(
            inv -> {
              String deviceId = inv.getArgument(0);
              return deviceDb.stream()
                  .filter(d -> d.getDeviceId().equalsIgnoreCase(deviceId))
                  .findFirst();
            });

    uz.ttpu.inventory.security.repository.RefreshTokenRepository refreshTokenRepository =
        mock(uz.ttpu.inventory.security.repository.RefreshTokenRepository.class);
    deviceService = new DeviceManagementService(deviceRepository, refreshTokenRepository);
    filter =
        new DeviceRevocationFilter(
            deviceService, new com.fasterxml.jackson.databind.ObjectMapper());
  }

  @Test
  @DisplayName("NFT-3.8: Mobile login registers device row and stays active")
  void testRegisterMobileDeviceOnLogin() {
    UserAccount user =
        UserAccount.builder()
            .username("alisher.navoiy")
            .email("alisher@ttpu.uz")
            .active(true)
            .build();
    user.setId(UUID.randomUUID());

    deviceService.registerOrUpdateDevice(
        user,
        "DEV-PIXEL-7A",
        "Google Pixel 7a",
        "ANDROID",
        "Android 14",
        "v2.1.0",
        "192.168.1.100",
        "TTPU-MobileApp/2.1");

    assertThat(deviceDb).hasSize(1);
    UserDevice registered = deviceDb.get(0);
    assertThat(registered.getDeviceId()).isEqualTo("DEV-PIXEL-7A");
    assertThat(registered.isRevoked()).isFalse();

    // Check device is not revoked
    assertThat(deviceService.isDeviceRevoked("DEV-PIXEL-7A")).isFalse();
  }

  @Test
  @DisplayName("NFT-3.8: Admin revokes device and subsequent token checks return revoked=true")
  void testRevokeDeviceInvalidatesAccess() {
    UserAccount user =
        UserAccount.builder().username("nodira.begim").email("nodira@ttpu.uz").active(true).build();
    user.setId(UUID.randomUUID());

    deviceService.registerOrUpdateDevice(
        user,
        "DEV-IPHONE-14",
        "iPhone 14 Pro",
        "IOS",
        "iOS 17.2",
        "v2.1.0",
        "10.0.0.5",
        "TTPU-MobileApp/2.1");

    UUID adminId = UUID.randomUUID();
    UserDeviceDto revokedDto = deviceService.revokeDevice("DEV-IPHONE-14", "Stolen phone report");

    assertThat(revokedDto.revoked()).isTrue();
    assertThat(revokedDto.revocationReason()).isEqualTo("Stolen phone report");
    assertThat(deviceService.isDeviceRevoked("DEV-IPHONE-14")).isTrue();
  }

  @Test
  @DisplayName(
      "NFT-3.8: DeviceRevocationFilter blocks requests carrying JWT with revoked deviceId and"
          + " returns 401")
  void testDeviceRevocationFilterBlocksRevokedDevice() throws Exception {
    UserAccount user =
        UserAccount.builder().username("victim.user").email("victim@ttpu.uz").active(true).build();
    user.setId(UUID.randomUUID());

    deviceService.registerOrUpdateDevice(
        user,
        "DEV-REVOKED-01",
        "Compromised Tablet",
        "ANDROID",
        "Android 13",
        "v2.0",
        "127.0.0.1",
        "App");

    // Revoke the device
    deviceService.revokeDevice("DEV-REVOKED-01", "Security breach");

    // Setup SecurityContext with JWT having deviceId claim
    Jwt jwt =
        new Jwt(
            "fake-token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "none"),
            Map.of("sub", "victim.user", "deviceId", "DEV-REVOKED-01"));
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, Collections.emptyList()));

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/assets");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    assertThat(response.getContentAsString()).contains("ERR_DEVICE_REVOKED");
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  @DisplayName("NFT-3.8: DeviceRevocationFilter allows active non-revoked device through")
  void testDeviceRevocationFilterAllowsActiveDevice() throws Exception {
    UserAccount user =
        UserAccount.builder().username("active.user").email("active@ttpu.uz").active(true).build();
    user.setId(UUID.randomUUID());

    deviceService.registerOrUpdateDevice(
        user, "DEV-ACTIVE-01", "Active Phone", "ANDROID", "Android 14", "v2.0", "127.0.0.1", "App");

    Jwt jwt =
        new Jwt(
            "fake-token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "none"),
            Map.of("sub", "active.user", "deviceId", "DEV-ACTIVE-01"));
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, Collections.emptyList()));

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/assets");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain filterChain = mock(FilterChain.class);

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    verify(filterChain).doFilter(request, response);
  }
}
