package uz.ttpu.inventory.asset.api;

public record PublicItemCardDto(
        String qrId,
        String name,
        String inventoryNumber,
        String ownershipLabel,
        String contactPhone
) {
    public static final String DEFAULT_OWNERSHIP_LABEL = "TTPU mulki";
}
