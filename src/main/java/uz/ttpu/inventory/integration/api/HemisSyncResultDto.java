package uz.ttpu.inventory.integration.api;

import java.time.Instant;

public record HemisSyncResultDto(int departmentsSynced, int employeesSynced, Instant syncedAt) {}
