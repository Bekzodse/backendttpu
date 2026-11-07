package uz.ttpu.inventory.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import jakarta.servlet.FilterChain;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import uz.ttpu.inventory.common.domain.BuildingScoped;
import uz.ttpu.inventory.common.domain.DepartmentScoped;
import uz.ttpu.inventory.security.api.LoginRequest;
import uz.ttpu.inventory.security.api.TokenResponse;
import uz.ttpu.inventory.security.domain.RateLimitExceededException;
import uz.ttpu.inventory.security.domain.RefreshToken;
import uz.ttpu.inventory.security.domain.RoleName;
import uz.ttpu.inventory.security.domain.UserAccount;
import uz.ttpu.inventory.security.repository.RefreshTokenRepository;
import uz.ttpu.inventory.security.repository.UserAccountRepository;
import uz.ttpu.inventory.security.service.AuthService;
import uz.ttpu.inventory.security.service.JwtTokenService;
import uz.ttpu.inventory.security.service.LoginAttemptService;
import uz.ttpu.inventory.security.service.LoginRateLimiterService;
import uz.ttpu.inventory.security.service.LoginRateLimitingFilter;
import uz.ttpu.inventory.security.service.ScopedPermissionEvaluator;
import uz.ttpu.inventory.security.service.SecurityConfig;
import uz.ttpu.inventory.security.service.SecurityProperties;
import uz.ttpu.inventory.security.service.TokenHashUtil;
import uz.ttpu.inventory.security.service.TotpService;
import uz.ttpu.inventory.security.service.UserSecurityMapper;

class SecurityModuleUnitTests {

    private TotpService createTotpService() {
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(
                new DefaultCodeGenerator(HashingAlgorithm.SHA1),
                new SystemTimeProvider()
        );
        verifier.setTimePeriod(30);
        verifier.setAllowedTimePeriodDiscrepancy(1);
        return new TotpService(new DefaultSecretGenerator(), verifier);
    }

    @Test
    void argon2HashesAndVerifiesPasswords() {
        Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        String raw = "S3cureP@ssw0rd!";
        String encoded = encoder.encode(raw);

        assertThat(encoded).startsWith("$argon2id$");
        assertThat(encoder.matches(raw, encoded)).isTrue();
        assertThat(encoder.matches("WrongPassword", encoded)).isFalse();
    }

    @Test
    void totpGeneratesAndVerifiesSixDigitCode() throws Exception {
        TotpService totpService = createTotpService();
        String secret = totpService.generateSecret();

        long currentBucket = Math.floorDiv(new SystemTimeProvider().getTime(), 30L);
        String validCode = new DefaultCodeGenerator(HashingAlgorithm.SHA1).generate(secret, currentBucket);

        assertThat(totpService.verifyCode(secret, validCode)).isTrue();
        assertThat(totpService.verifyCode(secret, "000000")).isFalse();
    }

    @Test
    void refreshTokenIsHashedWithSha256() {
        String rawToken = TokenHashUtil.generateRawRefreshToken();
        String hash1 = TokenHashUtil.sha256Hex(rawToken);
        String hash2 = TokenHashUtil.sha256Hex(rawToken);

        assertThat(rawToken).isNotBlank();
        assertThat(hash1).hasSize(64).isEqualTo(hash2).isNotEqualTo(rawToken);
    }

    @Test
    void loginIssuesJwtAndHashedRefreshTokenAndLocksAccountAfterFiveFailedAttempts() throws Exception {
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        RefreshTokenRepository refreshRepo = mock(RefreshTokenRepository.class);
        Argon2PasswordEncoder passwordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        TotpService totpService = createTotpService();
        SecurityProperties props = new SecurityProperties();

        SecurityConfig securityConfig = new SecurityConfig(
                new ScopedPermissionEvaluator(),
                null,
                props
        );
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        JwtEncoder jwtEncoder = securityConfig.jwtEncoder(kp);
        JwtDecoder jwtDecoder = securityConfig.jwtDecoder(kp);

        JwtTokenService jwtTokenService = new JwtTokenService(jwtEncoder, props);
        LoginAttemptService loginAttemptService = new LoginAttemptService(userRepo, props);
        UserSecurityMapper mapper = Mappers.getMapper(UserSecurityMapper.class);

        AuthService authService = new AuthService(
                userRepo,
                refreshRepo,
                passwordEncoder,
                totpService,
                jwtTokenService,
                loginAttemptService,
                mapper,
                props
        );

        UUID userId = UUID.randomUUID();
        String rawPassword = "CorrectHorseBatteryStaple!1";
        String totpSecret = totpService.generateSecret();

        UserAccount user = UserAccount.builder()
                .username("warehouse.mgr")
                .email("warehouse@ttpu.uz")
                .passwordHash(passwordEncoder.encode(rawPassword))
                .totpSecret(totpSecret)
                .totpEnabled(true)
                .active(true)
                .roles(Set.of(RoleName.ROLE_WAREHOUSE_MANAGER))
                .permissions(Set.of("ASSET_READ", "ASSET_RECEIVE"))
                .build();
        user.setId(userId);

        when(userRepo.findByUsernameIgnoreCase("warehouse.mgr")).thenReturn(Optional.of(user));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userRepo.saveAndFlush(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshRepo.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        long currentBucket = Math.floorDiv(new SystemTimeProvider().getTime(), 30L);
        String validTotp = new DefaultCodeGenerator(HashingAlgorithm.SHA1).generate(totpSecret, currentBucket);

        // 1. Successful login issues valid JWT access token and stores SHA-256 hash of refresh token
        TokenResponse response = authService.login(
                new LoginRequest("warehouse.mgr", rawPassword, validTotp),
                "127.0.0.1",
                "JUnit-Test"
        );

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        Jwt decodedJwt = jwtDecoder.decode(response.accessToken());
        assertThat(decodedJwt.getSubject()).isEqualTo("warehouse.mgr");

        ArgumentCaptor<RefreshToken> refreshCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshRepo).save(refreshCaptor.capture());
        RefreshToken savedRefreshToken = refreshCaptor.getValue();
        assertThat(savedRefreshToken.getTokenHash())
                .isEqualTo(TokenHashUtil.sha256Hex(response.refreshToken()))
                .isNotEqualTo(response.refreshToken());

        // 2. 5 failed login attempts lock the account on the 5th attempt
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThatThrownBy(() -> authService.login(
                    new LoginRequest("warehouse.mgr", "WrongPassword", validTotp),
                    "127.0.0.1",
                    "JUnit-Test"
            )).isInstanceOf(BadCredentialsException.class);
            assertThat(user.getFailedLoginAttempts()).isEqualTo(attempt);
            assertThat(user.getLockedUntil()).isNull();
        }

        // 5th failed attempt triggers lockout
        assertThatThrownBy(() -> authService.login(
                new LoginRequest("warehouse.mgr", "WrongPassword", validTotp),
                "127.0.0.1",
                "JUnit-Test"
        )).isInstanceOf(LockedException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isAfter(Instant.now());

        // Subsequent attempt even with valid credentials is rejected while locked
        assertThatThrownBy(() -> authService.login(
                new LoginRequest("warehouse.mgr", rawPassword, validTotp),
                "127.0.0.1",
                "JUnit-Test"
        )).isInstanceOf(LockedException.class);
    }

    @Test
    void scopedPermissionEvaluatorRestrictsByDepartmentAndBuildingScope() {
        ScopedPermissionEvaluator evaluator = new ScopedPermissionEvaluator();
        UUID allowedDeptId = UUID.randomUUID();
        UUID otherDeptId = UUID.randomUUID();
        UUID allowedBuildingId = UUID.randomUUID();

        Jwt jwt = Jwt.withTokenValue("mock-jwt-token")
                .header("alg", "RS256")
                .subject("dept.head")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .claim("department_id", allowedDeptId.toString())
                .claim("building_id", allowedBuildingId.toString())
                .claim("roles", List.of(RoleName.ROLE_DEPARTMENT_HEAD.name()))
                .claim("permissions", List.of("ASSET_READ", "ASSET_REQUEST"))
                .build();

        JwtAuthenticationToken auth = new JwtAuthenticationToken(
                jwt,
                Set.of(
                        new SimpleGrantedAuthority(RoleName.ROLE_DEPARTMENT_HEAD.name()),
                        new SimpleGrantedAuthority("ASSET_READ"),
                        new SimpleGrantedAuthority("ASSET_REQUEST")
                )
        );

        assertThat(evaluator.hasPermission(auth, allowedDeptId, "DEPARTMENT", "ASSET_READ")).isTrue();
        assertThat(evaluator.hasPermission(auth, otherDeptId, "DEPARTMENT", "ASSET_READ")).isFalse();
        assertThat(evaluator.hasPermission(auth, allowedDeptId, "DEPARTMENT", "ASSET_DELETE")).isFalse();
        assertThat(evaluator.hasPermission(auth, allowedBuildingId, "BUILDING", "ASSET_READ")).isTrue();

        DepartmentScoped matchingResource = () -> allowedDeptId;
        DepartmentScoped foreignResource = () -> otherDeptId;

        assertThat(evaluator.hasPermission(auth, matchingResource, "ASSET_READ")).isTrue();
        assertThat(evaluator.hasPermission(auth, foreignResource, "ASSET_READ")).isFalse();

        // Dual-scoped resource (implements both DepartmentScoped and BuildingScoped, like Item / ItemDto)
        // tested with a user who ONLY has building_id (e.g. Building Commandant)
        Jwt commandantJwt = Jwt.withTokenValue("mock-commandant-jwt")
                .header("alg", "RS256")
                .subject("bldg.commandant")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .claim("building_id", allowedBuildingId.toString())
                .claim("roles", List.of(RoleName.ROLE_BUILDING_COMMANDANT.name()))
                .claim("permissions", List.of("ASSET_READ"))
                .build();
        JwtAuthenticationToken commandantAuth = new JwtAuthenticationToken(
                commandantJwt,
                Set.of(
                        new SimpleGrantedAuthority(RoleName.ROLE_BUILDING_COMMANDANT.name()),
                        new SimpleGrantedAuthority("ASSET_READ")
                )
        );

        record DualScopedResource(UUID departmentId, UUID buildingId) implements DepartmentScoped, BuildingScoped {
            @Override
            public UUID getDepartmentId() {
                return departmentId;
            }

            @Override
            public UUID getBuildingId() {
                return buildingId;
            }
        }

        DualScopedResource itemInAllowedBuilding = new DualScopedResource(otherDeptId, allowedBuildingId);
        DualScopedResource itemInOtherBuilding = new DualScopedResource(otherDeptId, UUID.randomUUID());

        assertThat(evaluator.hasPermission(commandantAuth, itemInAllowedBuilding, "ASSET_READ")).isTrue();
        assertThat(evaluator.hasPermission(commandantAuth, itemInOtherBuilding, "ASSET_READ")).isFalse();
    }

    @Test
    void bucket4jLoginRateLimiterEnforcesStrictLimitAndReturnsHttp429WithRetryAfter() throws Exception {
        SecurityProperties props = new SecurityProperties();
        props.setLoginRateLimitCapacity(3);
        props.setLoginRateLimitWindow(Duration.ofMinutes(1));

        LoginRateLimiterService rateLimiterService = new LoginRateLimiterService(props);
        LoginRateLimitingFilter filter = new LoginRateLimitingFilter(rateLimiterService, new ObjectMapper());
        FilterChain filterChain = mock(FilterChain.class);

        // First 3 login attempts from 10.10.1.55 succeed and include X-Rate-Limit-Remaining header
        for (int expectedRemaining = 2; expectedRemaining >= 0; expectedRemaining--) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setServletPath("/api/v1/auth/login");
            req.setRemoteAddr("10.10.1.55");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilter(req, res, filterChain);

            assertThat(res.getStatus()).isEqualTo(200);
            assertThat(res.getHeader("X-Rate-Limit-Remaining")).isEqualTo(String.valueOf(expectedRemaining));
        }

        // 4th attempt from 10.10.1.55 is blocked by Bucket4j with HTTP 429 Too Many Requests & Retry-After header
        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        blockedReq.setServletPath("/api/v1/auth/login");
        blockedReq.setRemoteAddr("10.10.1.55");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();

        filter.doFilter(blockedReq, blockedRes, filterChain);

        assertThat(blockedRes.getStatus()).isEqualTo(429);
        assertThat(blockedRes.getHeader("Retry-After")).isNotBlank();
        assertThat(blockedRes.getContentAsString()).contains("Too Many Requests");

        // Direct consumeOrThrow also throws RateLimitExceededException when bucket is exhausted
        assertThatThrownBy(() -> rateLimiterService.consumeOrThrow("/api/v1/auth/login:10.10.1.55"))
                .isInstanceOf(RateLimitExceededException.class);

        // A different client IP has its own isolated Bucket4j token bucket
        MockHttpServletRequest otherIpReq = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        otherIpReq.setServletPath("/api/v1/auth/login");
        otherIpReq.setRemoteAddr("10.10.1.99");
        MockHttpServletResponse otherIpRes = new MockHttpServletResponse();

        filter.doFilter(otherIpReq, otherIpRes, filterChain);
        assertThat(otherIpRes.getStatus()).isEqualTo(200);
    }
}
