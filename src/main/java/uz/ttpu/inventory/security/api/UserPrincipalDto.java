package uz.ttpu.inventory.security.api;

import java.util.Set;
import java.util.UUID;
import uz.ttpu.inventory.security.domain.RoleName;

public record UserPrincipalDto(
        UUID id,
        String username,
        String email,
        UUID employeeId,
        UUID departmentId,
        UUID buildingId,
        Set<RoleName> roles,
        Set<String> permissions
) {
}
