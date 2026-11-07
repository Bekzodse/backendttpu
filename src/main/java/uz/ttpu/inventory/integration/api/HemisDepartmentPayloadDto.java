package uz.ttpu.inventory.integration.api;

import jakarta.validation.constraints.NotBlank;

public record HemisDepartmentPayloadDto(@NotBlank String code, @NotBlank String name) {}
