package uz.ttpu.inventory.integration.api;

public record PilotMigrationMismatchDto(
        int rowNumber,
        String inventoryNumber,
        String mismatchType,
        String columnName,
        String rawValue,
        String message
) {
}
