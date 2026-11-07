package uz.ttpu.inventory.integration.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import uz.ttpu.inventory.integration.domain.ExternalSystemType;
import uz.ttpu.inventory.integration.domain.OutboxStatus;

public record OutboxMessageDto(
        UUID id,
        String idempotencyKey,
        ExternalSystemType targetSystem,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String endpointUrl,
        String httpMethod,
        Map<String, Object> payload,
        OutboxStatus status,
        int attemptCount,
        int maxAttempts,
        Instant nextAttemptAt,
        Instant processedAt,
        String lastError,
        Integer responseStatusCode,
        String responseBody,
        Instant createdAt,
        Instant updatedAt
) {
}
