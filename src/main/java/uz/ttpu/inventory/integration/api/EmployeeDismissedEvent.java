package uz.ttpu.inventory.integration.api;

import java.time.Instant;
import java.util.UUID;

public record EmployeeDismissedEvent(
    UUID employeeId, String orderNumber, String reason, Instant dismissedAt) {}
