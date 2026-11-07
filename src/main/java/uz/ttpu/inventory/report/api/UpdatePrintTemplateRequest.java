package uz.ttpu.inventory.report.api;

import jakarta.validation.constraints.NotBlank;

public record UpdatePrintTemplateRequest(
    @NotBlank(message = "xhtmlContent must not be blank") String xhtmlContent) {}
