package uz.ttpu.inventory.security.service;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import uz.ttpu.inventory.security.domain.RoleName;
import uz.ttpu.inventory.security.domain.UserAccount;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties securityProperties;

    public String generateAccessToken(UserAccount user, Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(securityProperties.getAccessTokenTtl());
        List<String> roles = user.getRoles().stream().map(RoleName::name).sorted().toList();
        List<String> permissions = user.getPermissions().stream().sorted().toList();

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer(securityProperties.getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim("user_id", user.getId().toString())
                .claim("roles", roles)
                .claim("permissions", permissions);

        if (user.getEmployeeId() != null) {
            claimsBuilder.claim("employee_id", user.getEmployeeId().toString());
        }
        if (user.getDepartmentId() != null) {
            claimsBuilder.claim("department_id", user.getDepartmentId().toString());
        }
        if (user.getBuildingId() != null) {
            claimsBuilder.claim("building_id", user.getBuildingId().toString());
        }

        return jwtEncoder.encode(JwtEncoderParameters.from(claimsBuilder.build())).getTokenValue();
    }

    public long getAccessTokenTtlSeconds() {
        return securityProperties.getAccessTokenTtl().toSeconds();
    }
}
