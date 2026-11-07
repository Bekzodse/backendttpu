package uz.ttpu.inventory.integration.api;

import java.math.BigDecimal;
import java.util.List;

public record OneCImportRowPreviewDto(
        int rowNumber,
        String inventoryNumber,
        String assetName,
        String serialNumber,
        String model,
        String categoryCode,
        String categoryName,
        String buildingCode,
        String floorNumber,
        String roomNumber,
        String departmentCode,
        String employeeCode,
        String employeeFullName,
        BigDecimal purchasePrice,
        BigDecimal residualValue,
        String oneCRefId,
        boolean valid,
        boolean duplicate,
        List<String> errors,
        List<String> warnings
) {
}
