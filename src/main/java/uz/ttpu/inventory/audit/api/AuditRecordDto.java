package uz.ttpu.inventory.audit.api;

import java.time.Instant;
import java.util.UUID;
import uz.ttpu.inventory.common.domain.AuditActionType;

public record AuditRecordDto(
    UUID id,
    AuditActionType actionType,
    String targetType,
    String targetId,
    String methodName,
    String actorUsername,
    String details,
    Instant occurredAt) {}
