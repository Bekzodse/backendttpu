package uz.ttpu.inventory.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.security.domain.RateLimitExceededException;
import uz.ttpu.inventory.security.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(
    name = "01. Security & Authentication",
    description =
        "Stateless OAuth2 JWT authentication, Argon2id password verification, mandatory 2FA TOTP"
            + " verification, SHA-256 refresh token rotation, 5-attempt account lockout, and"
            + " Bucket4j rate limiting.")
public class AuthController {

  private final AuthService authService;

  @PostMapping("/login")
  @SecurityRequirements
  @Operation(
      summary = "Authenticate user with password and 2FA TOTP",
      description =
          "Verifies username, Argon2id password hash, and mandatory 6-digit TOTP code. Issues a"
              + " short-lived RSA-2048 JWT access token and stores a SHA-256 hashed refresh token."
              + " Locks the account for 15 minutes after 5 consecutive failed attempts and enforces"
              + " Bucket4j IP rate limiting.")
  public ResponseEntity<TokenResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    String ipAddress = servletRequest.getRemoteAddr();
    String userAgent = servletRequest.getHeader("User-Agent");
    return ResponseEntity.ok(authService.login(request, ipAddress, userAgent));
  }

  @PostMapping("/refresh")
  @SecurityRequirements
  @Operation(
      summary = "Rotate refresh token and issue a new JWT access token",
      description =
          "Validates the raw refresh token against its stored SHA-256 hash, revokes the used"
              + " refresh token, and issues a fresh JWT access token and rotated refresh token.")
  public ResponseEntity<TokenResponse> refresh(
      @Valid @RequestBody RefreshTokenRequest request, HttpServletRequest servletRequest) {
    String ipAddress = servletRequest.getRemoteAddr();
    String userAgent = servletRequest.getHeader("User-Agent");
    return ResponseEntity.ok(authService.refreshToken(request, ipAddress, userAgent));
  }

  @PostMapping("/logout")
  @SecurityRequirements
  @Operation(
      summary = "Revoke active refresh token and log out",
      description =
          "Marks the matching SHA-256 refresh token record as revoked in PostgreSQL so it can no"
              + " longer be used to issue new access tokens.")
  public ResponseEntity<Map<String, String>> logout(
      @Valid @RequestBody RefreshTokenRequest request) {
    authService.logout(request);
    return ResponseEntity.ok(Map.of("status", "logged_out"));
  }

  @PostMapping("/2fa/setup")
  @Operation(
      summary = "Generate mandatory 2FA TOTP secret and provisioning URI",
      description =
          "Generates a new Base32 TOTP secret and otpauth:// QR provisioning URI for the"
              + " authenticated user account.")
  public ResponseEntity<TotpSetupResponse> setupTwoFactor(Authentication authentication) {
    return ResponseEntity.ok(authService.setupTwoFactor(authentication.getName()));
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    problem.setTitle("Authentication Failed");
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
  }

  @ExceptionHandler({LockedException.class, DisabledException.class})
  public ResponseEntity<ProblemDetail> handleLockedOrDisabled(RuntimeException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.LOCKED, ex.getMessage());
    problem.setTitle("Account Locked or Disabled");
    return ResponseEntity.status(HttpStatus.LOCKED).body(problem);
  }

  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<ProblemDetail> handleRateLimitExceeded(RateLimitExceededException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    problem.setTitle("Too Many Requests");
    problem.setProperty("retryAfterSeconds", ex.getRetryAfterSeconds());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
        .body(problem);
  }
}
