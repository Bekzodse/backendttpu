package uz.ttpu.inventory.report.api;

import java.time.Instant;

public record PrintTemplateDto(
    String templateKey,
    String formCode,
    String description,
    String xhtmlContent,
    boolean customized,
    Instant updatedAt) {}
