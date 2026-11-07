package uz.ttpu.inventory.finance.api;

import jakarta.validation.constraints.NotBlank;

public record WriteOffCompletionRequest(
    @NotBlank String documentNumber, String reason, String performerName, String wipeMethod) {}
