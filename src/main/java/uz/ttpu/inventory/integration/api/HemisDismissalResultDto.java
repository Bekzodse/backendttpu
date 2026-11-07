package uz.ttpu.inventory.integration.api;

import java.time.Instant;
import java.util.UUID;

public record HemisDismissalResultDto(
    UUID employeeId, String checklistNumber, String status, Instant timestamp) {}
