package uz.ttpu.inventory.report.api;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record AttachPdfSignatureRequest(
    @NotBlank String documentType,
    UUID documentId,
    @NotBlank String documentNumber,
    @NotBlank String formCode,
    @NotBlank String fileName,
    @NotBlank String pdfBase64,
    @NotBlank String signerPinfl,
    String signerTin,
    @NotBlank String signerFullName,
    String signerOrganization,
    String certificateSerialNumber) {}
