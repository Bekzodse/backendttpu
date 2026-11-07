package uz.ttpu.inventory.integration.api;

import java.time.Instant;
import java.util.Map;

public record OutboxWorkerRunSummaryDto(
        int processedCount,
        int failedCount,
        int deadLetterCount,
        Map<String, String> circuitBreakerStates,
        Instant executedAt
) {
}
