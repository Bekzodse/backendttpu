package uz.ttpu.inventory.integration.api;

public record OneCReconciliationMismatchDto(
    String inventoryNumber,
    String mismatchType,
    String localValue,
    String oneCValue,
    String details) {}
