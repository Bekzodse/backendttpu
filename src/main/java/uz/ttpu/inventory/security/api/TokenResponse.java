package uz.ttpu.inventory.security.api;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserPrincipalDto user
) {
}
