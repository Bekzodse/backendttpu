package uz.ttpu.inventory.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.ttpu.inventory.common.domain.ErrorCode;
import uz.ttpu.inventory.common.domain.ProblemDetailFactory;

@Component
@RequiredArgsConstructor
public class DeviceRevocationFilter extends OncePerRequestFilter {

  private final DeviceManagementService deviceManagementService;
  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
      String deviceId = jwt.getClaimAsString("deviceId");
      if (deviceId != null
          && !deviceId.isBlank()
          && deviceManagementService.isDeviceRevoked(deviceId)) {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail problem =
            ProblemDetailFactory.create(
                ErrorCode.ERR_DEVICE_REVOKED,
                "Device '" + deviceId + "' has been revoked by an administrator. Access is denied.",
                request.getLocale(),
                URI.create(request.getRequestURI()));
        objectMapper.writeValue(response.getOutputStream(), problem);
        return;
      }
    }

    filterChain.doFilter(request, response);
  }
}
