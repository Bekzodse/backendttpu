package uz.ttpu.inventory.integration.api;

import java.math.BigDecimal;

public record OneCAssetRecordDto(
    String inventoryNumber,
    String name,
    BigDecimal financialValue,
    String responsiblePerson,
    String oneCRefId) {}
