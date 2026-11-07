package uz.ttpu.inventory.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.ttpu.inventory.common.domain.ErrorCode;
import uz.ttpu.inventory.common.domain.ProblemDetailFactory;

@Component
@RequiredArgsConstructor
public class LoginRateLimitingFilter extends OncePerRequestFilter {

  private static final Set<String> RATE_LIMITED_AUTH_PATHS =
      Set.of("/api/v1/auth/login", "/api/v1/auth/refresh");

  private final LoginRateLimiterService loginRateLimiterService;
  private final ObjectMapper objectMapper;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    if (path == null || path.isBlank()) {
      path = request.getRequestURI();
    }
    return path == null || !RATE_LIMITED_AUTH_PATHS.contains(path);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String clientIp = extractClientIp(request);
    String bucketKey = request.getRequestURI() + ":" + clientIp;

    ConsumptionProbe probe = loginRateLimiterService.tryConsume(bucketKey);
    if (probe.isConsumed()) {
      response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
      filterChain.doFilter(request, response);
      return;
    }

    long retryAfterSeconds =
        Math.max(1L, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
    response.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(retryAfterSeconds));

    String requestUri =
        (request.getRequestURI() != null && !request.getRequestURI().isBlank())
            ? request.getRequestURI()
            : "/api/v1/auth/login";
    ProblemDetail problem =
        ProblemDetailFactory.create(
            ErrorCode.ERR_RATE_LIMIT_EXCEEDED,
            "Too many authentication attempts from "
                + clientIp
                + ". Retry after "
                + retryAfterSeconds
                + " seconds.",
            request.getLocale(),
            URI.create(requestUri));
    problem.setProperty("retryAfterSeconds", retryAfterSeconds);

    objectMapper.writeValue(response.getOutputStream(), problem);
  }

  private String extractClientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    String remoteAddr = request.getRemoteAddr();
    return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "127.0.0.1";
  }
}
