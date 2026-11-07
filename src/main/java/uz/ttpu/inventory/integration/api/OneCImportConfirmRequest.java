package uz.ttpu.inventory.integration.api;

public record OneCImportConfirmRequest(
        boolean skipInvalidRows,
        boolean autoCreateMissingCategories
) {
}
