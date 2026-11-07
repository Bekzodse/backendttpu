package uz.ttpu.inventory.security.service;

import java.io.Serializable;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import uz.ttpu.inventory.common.domain.BuildingScoped;
import uz.ttpu.inventory.common.domain.DepartmentScoped;
import uz.ttpu.inventory.security.domain.RoleName;
import uz.ttpu.inventory.security.domain.ScopeType;

@Component
public class ScopedPermissionEvaluator implements PermissionEvaluator {

    private static final Set<String> GLOBAL_SCOPE_ROLES = Set.of(
            RoleName.ROLE_ADMIN.name(),
            RoleName.ROLE_RECTOR.name(),
            RoleName.ROLE_VICE_RECTOR.name(),
            RoleName.ROLE_ACCOUNTANT.name(),
            RoleName.ROLE_AUDITOR.name()
    );

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || !authentication.isAuthenticated() || permission == null) {
            return false;
        }

        String requiredPermission = permission.toString();
        if (!hasAuthorityOrAdmin(authentication, requiredPermission)) {
            return false;
        }

        if (hasGlobalScope(authentication) || targetDomainObject == null) {
            return true;
        }

        boolean isDeptScoped = targetDomainObject instanceof DepartmentScoped;
        boolean isBuildingScoped = targetDomainObject instanceof BuildingScoped;
        if (!isDeptScoped && !isBuildingScoped) {
            return true;
        }

        UUID userDepartmentId = extractClaimAsUuid(authentication, "department_id");
        UUID userBuildingId = extractClaimAsUuid(authentication, "building_id");
        boolean matchedAtLeastOneScope = false;

        if (isDeptScoped && userDepartmentId != null) {
            DepartmentScoped departmentScoped = (DepartmentScoped) targetDomainObject;
            if (!userDepartmentId.equals(departmentScoped.getDepartmentId())) {
                return false;
            }
            matchedAtLeastOneScope = true;
        }

        if (isBuildingScoped && userBuildingId != null) {
            BuildingScoped buildingScoped = (BuildingScoped) targetDomainObject;
            if (!userBuildingId.equals(buildingScoped.getBuildingId())) {
                return false;
            }
            matchedAtLeastOneScope = true;
        }

        return matchedAtLeastOneScope;
    }

    @Override
    public boolean hasPermission(
            Authentication authentication,
            Serializable targetId,
            String targetType,
            Object permission
    ) {
        if (authentication == null || !authentication.isAuthenticated() || permission == null) {
            return false;
        }

        String requiredPermission = permission.toString();
        if (!hasAuthorityOrAdmin(authentication, requiredPermission)) {
            return false;
        }

        if (hasGlobalScope(authentication) || targetType == null || targetId == null) {
            return true;
        }

        UUID targetUuid = toUuid(targetId);
        if (targetUuid == null) {
            return false;
        }

        String normalizedType = targetType.trim().toUpperCase();
        if (ScopeType.DEPARTMENT.name().equals(normalizedType)) {
            UUID userDepartmentId = extractClaimAsUuid(authentication, "department_id");
            return targetUuid.equals(userDepartmentId);
        }

        if (ScopeType.BUILDING.name().equals(normalizedType)) {
            UUID userBuildingId = extractClaimAsUuid(authentication, "building_id");
            return targetUuid.equals(userBuildingId);
        }

        return true;
    }

    private boolean hasAuthorityOrAdmin(Authentication authentication, String requiredPermission) {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        for (GrantedAuthority authority : authorities) {
            String auth = authority.getAuthority();
            if (RoleName.ROLE_ADMIN.name().equals(auth) || requiredPermission.equals(auth)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasGlobalScope(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (GLOBAL_SCOPE_ROLES.contains(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private UUID extractClaimAsUuid(Authentication authentication, String claimName) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String claimValue = jwt.getClaimAsString(claimName);
            if (claimValue != null && !claimValue.isBlank()) {
                return toUuid(claimValue);
            }
        }
        return null;
    }

    private UUID toUuid(Serializable value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
