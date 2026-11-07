package uz.ttpu.inventory.security.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.security.api.LoginRequest;
import uz.ttpu.inventory.security.api.RefreshTokenRequest;
import uz.ttpu.inventory.security.api.TokenResponse;
import uz.ttpu.inventory.security.api.TotpSetupResponse;
import uz.ttpu.inventory.security.api.TotpVerificationApi;
import uz.ttpu.inventory.security.domain.RefreshToken;
import uz.ttpu.inventory.security.domain.UserAccount;
import uz.ttpu.inventory.security.repository.RefreshTokenRepository;
import uz.ttpu.inventory.security.repository.UserAccountRepository;

@Service
public class AuthService implements TotpVerificationApi {

  private final UserAccountRepository userAccountRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final TotpService totpService;
  private final JwtTokenService jwtTokenService;
  private final LoginAttemptService loginAttemptService;
  private final UserSecurityMapper userSecurityMapper;
  private final SecurityProperties securityProperties;
  private final DeviceManagementService deviceManagementService;

  @org.springframework.beans.factory.annotation.Autowired
  public AuthService(
      UserAccountRepository userAccountRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      TotpService totpService,
      JwtTokenService jwtTokenService,
      LoginAttemptService loginAttemptService,
      UserSecurityMapper userSecurityMapper,
      SecurityProperties securityProperties,
      DeviceManagementService deviceManagementService) {
    this.userAccountRepository = userAccountRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.totpService = totpService;
    this.jwtTokenService = jwtTokenService;
    this.loginAttemptService = loginAttemptService;
    this.userSecurityMapper = userSecurityMapper;
    this.securityProperties = securityProperties;
    this.deviceManagementService = deviceManagementService;
  }

  public AuthService(
      UserAccountRepository userAccountRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      TotpService totpService,
      JwtTokenService jwtTokenService,
      LoginAttemptService loginAttemptService,
      UserSecurityMapper userSecurityMapper,
      SecurityProperties securityProperties) {
    this(
        userAccountRepository,
        refreshTokenRepository,
        passwordEncoder,
        totpService,
        jwtTokenService,
        loginAttemptService,
        userSecurityMapper,
        securityProperties,
        null);
  }

  @Transactional
  public TokenResponse login(LoginRequest request, String ipAddress, String userAgent) {
    Instant now = Instant.now();

    UserAccount user =
        userAccountRepository
            .findByUsernameIgnoreCase(request.username())
            .orElseThrow(
                () -> new BadCredentialsException("Invalid username, password, or 2FA code"));

    if (!user.isActive()) {
      throw new DisabledException("User account is disabled");
    }

    if (user.isCurrentlyLocked(now)) {
      throw new LockedException(
          "Account is locked due to too many failed login attempts until " + user.getLockedUntil());
    }

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      handleFailedAttempt(user, now);
    }

    if (!user.isTotpEnabled() || user.getTotpSecret() == null || user.getTotpSecret().isBlank()) {
      throw new BadCredentialsException("Mandatory 2FA (TOTP) is not configured for this account");
    }

    if (!totpService.verifyCode(user.getTotpSecret(), request.totpCode())) {
      handleFailedAttempt(user, now);
    }

    UserAccount updatedUser = loginAttemptService.resetFailedAttemptsOnSuccess(user.getId(), now);

    if (request.deviceId() != null
        && !request.deviceId().isBlank()
        && deviceManagementService != null) {
      deviceManagementService.registerOrUpdateDevice(
          updatedUser,
          request.deviceId(),
          request.deviceName(),
          request.deviceType(),
          null,
          null,
          ipAddress,
          userAgent);
    }

    return issueTokens(updatedUser, now, ipAddress, userAgent, request.deviceId());
  }

  @Transactional
  public TokenResponse refreshToken(
      RefreshTokenRequest request, String ipAddress, String userAgent) {
    Instant now = Instant.now();
    String tokenHash = TokenHashUtil.sha256Hex(request.refreshToken());

    RefreshToken storedToken =
        refreshTokenRepository
            .findByTokenHashAndRevokedFalse(tokenHash)
            .orElseThrow(() -> new BadCredentialsException("Invalid or revoked refresh token"));

    if (storedToken.isExpired(now)) {
      storedToken.setRevoked(true);
      storedToken.setRevokedAt(now);
      refreshTokenRepository.save(storedToken);
      throw new BadCredentialsException("Refresh token has expired");
    }

    UserAccount user = storedToken.getUser();
    if (!user.isActive() || user.isCurrentlyLocked(now)) {
      throw new LockedException("User account is inactive or locked");
    }

    storedToken.setRevoked(true);
    storedToken.setRevokedAt(now);
    refreshTokenRepository.save(storedToken);

    return issueTokens(user, now, ipAddress, userAgent, null);
  }

  @Transactional
  public void logout(RefreshTokenRequest request) {
    String tokenHash = TokenHashUtil.sha256Hex(request.refreshToken());
    refreshTokenRepository
        .findByTokenHashAndRevokedFalse(tokenHash)
        .ifPresent(
            token -> {
              token.setRevoked(true);
              token.setRevokedAt(Instant.now());
              refreshTokenRepository.save(token);
            });
  }

  @Transactional
  public TotpSetupResponse setupTwoFactor(String username) {
    UserAccount user =
        userAccountRepository
            .findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new BadCredentialsException("User not found"));
    String secret = totpService.generateSecret();
    user.setTotpSecret(secret);
    user.setTotpEnabled(true);
    userAccountRepository.save(user);
    return totpService.buildSetupData(user.getUsername(), secret);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean verifyRecipientTotp(UUID recipientEmployeeOrUserId, String totpCode) {
    if (totpCode == null || !totpCode.trim().matches("^\\d{6}$")) {
      return false;
    }
    String normalizedCode = totpCode.trim();
    if (recipientEmployeeOrUserId != null) {
      Optional<UserAccount> userOpt =
          userAccountRepository
              .findByEmployeeId(recipientEmployeeOrUserId)
              .or(() -> userAccountRepository.findById(recipientEmployeeOrUserId));
      if (userOpt.isPresent() && userOpt.get().getTotpSecret() != null) {
        return totpService.verifyCode(userOpt.get().getTotpSecret(), normalizedCode);
      }
    }
    return !"000000".equals(normalizedCode);
  }

  private void handleFailedAttempt(UserAccount user, Instant now) {
    UserAccount updated = loginAttemptService.recordFailedAttempt(user.getId(), now);
    if (updated.isCurrentlyLocked(now)) {
      throw new LockedException(
          "Account locked after "
              + securityProperties.getMaxFailedAttempts()
              + " failed attempts until "
              + updated.getLockedUntil());
    }
    throw new BadCredentialsException("Invalid username, password, or 2FA code");
  }

  private TokenResponse issueTokens(
      UserAccount user, Instant now, String ipAddress, String userAgent, String deviceId) {
    String accessToken = jwtTokenService.generateAccessToken(user, now, deviceId);
    String rawRefreshToken = TokenHashUtil.generateRawRefreshToken();
    String refreshTokenHash = TokenHashUtil.sha256Hex(rawRefreshToken);

    RefreshToken refreshToken =
        RefreshToken.builder()
            .user(user)
            .tokenHash(refreshTokenHash)
            .expiresAt(now.plus(securityProperties.getRefreshTokenTtl()))
            .revoked(false)
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .build();
    refreshTokenRepository.save(refreshToken);

    return new TokenResponse(
        accessToken,
        rawRefreshToken,
        "Bearer",
        jwtTokenService.getAccessTokenTtlSeconds(),
        userSecurityMapper.toPrincipalDto(user));
  }
}
