package uz.ttpu.inventory.security.service;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

  private String issuer = "raqamli-inventar";
  private Duration accessTokenTtl = Duration.ofMinutes(15);
  private Duration refreshTokenTtl = Duration.ofDays(7);
  private int maxFailedAttempts = 5;
  private Duration lockoutDuration = Duration.ofMinutes(15);
  private int loginRateLimitCapacity = 10;
  private Duration loginRateLimitWindow = Duration.ofMinutes(1);
  private String contentSecurityPolicy =
      "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline';"
          + " img-src 'self' data:; frame-ancestors 'none'; object-src 'none'; base-uri 'self'";
}
