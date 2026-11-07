package uz.ttpu.inventory.security.api;

public record TotpSetupResponse(
        String secret,
        String otpAuthUri
) {
}
