package uz.ttpu.inventory.workflow.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import uz.ttpu.inventory.workflow.domain.VerificationMethod;

public record StepApprovalRequest(
    @NotNull VerificationMethod verificationMethod,
    @NotBlank(message = "E-IMZO PKCS#7 signature or 6-digit TOTP code is required")
        String signatureOrTotpCode,
    String comment,
    UUID decidedByUserId,
    String decidedByUsername) {}
