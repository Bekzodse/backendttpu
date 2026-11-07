package uz.ttpu.inventory.integration.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import uz.ttpu.inventory.integration.domain.ExternalSystemType;

public record EnqueueOutboxRequest(
        @NotBlank String idempotencyKey,
        @NotNull ExternalSystemType targetSystem,
        @NotBlank String aggregateType,
        @NotNull UUID aggregateId,
        @NotBlank String eventType,
        @NotBlank String endpointUrl,
        String httpMethod,
        @NotNull Map<String, Object> payload,
        Integer maxAttempts
) {
}
