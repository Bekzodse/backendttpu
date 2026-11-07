package uz.ttpu.inventory.integration.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record VerifySignedPdfRequest(
    @NotBlank String documentType,
    @NotNull UUID documentId,
    String documentNumber,
    @NotBlank String signedPdfBase64,
    String signerPinfl,
    String signerTin,
    String signerFullName,
    String signerOrganization,
    String certificateSerialNumber) {}
