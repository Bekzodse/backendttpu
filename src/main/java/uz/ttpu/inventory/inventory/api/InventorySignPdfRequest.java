package uz.ttpu.inventory.inventory.api;

import jakarta.validation.constraints.NotBlank;

public record InventorySignPdfRequest(
    @NotBlank(message = "signerPinfl is required") String signerPinfl,
    String signerTin,
    @NotBlank(message = "signerFullName is required") String signerFullName,
    String signerOrganization,
    @NotBlank(message = "certificateSerialNumber is required") String certificateSerialNumber) {}
