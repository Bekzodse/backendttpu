package uz.ttpu.inventory.references.api;

import java.time.Instant;

public record ReferenceRevisionDto<T>(
    long revisionNumber, Instant revisionTimestamp, String revisionType, T snapshot) {}
