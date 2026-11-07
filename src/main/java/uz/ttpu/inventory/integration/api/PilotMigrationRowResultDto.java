package uz.ttpu.inventory.integration.api;

import java.util.List;
import java.util.UUID;

public record PilotMigrationRowResultDto(
    int rowNumber,
    String inventoryNumber,
    String assetName,
    boolean valid,
    boolean imported,
    UUID itemId,
    String qrCode,
    List<PilotMigrationMismatchDto> mismatches) {}
