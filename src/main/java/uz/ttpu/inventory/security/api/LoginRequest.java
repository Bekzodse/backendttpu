package uz.ttpu.inventory.security.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
    @NotBlank(message = "Username is required") String username,
    @NotBlank(message = "Password is required") String password,
    @NotBlank(message = "2FA TOTP code is required")
        @Pattern(regexp = "^\\d{6}$", message = "TOTP code must be 6 digits")
        String totpCode,
    String deviceId,
    String deviceName,
    String deviceType) {

  public LoginRequest(String username, String password, String totpCode) {
    this(username, password, totpCode, null, null, null);
  }
}
