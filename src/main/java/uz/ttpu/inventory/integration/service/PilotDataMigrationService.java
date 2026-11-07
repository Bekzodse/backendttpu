package uz.ttpu.inventory.integration.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.asset.api.PilotItemUpsertRequest;
import uz.ttpu.inventory.integration.api.PilotMigrationImportRequest;
import uz.ttpu.inventory.integration.api.PilotMigrationMismatchDto;
import uz.ttpu.inventory.integration.api.PilotMigrationResultDto;
import uz.ttpu.inventory.integration.api.PilotMigrationRowResultDto;
import uz.ttpu.inventory.references.api.BuildingDto;
import uz.ttpu.inventory.references.api.CategoryDto;
import uz.ttpu.inventory.references.api.DepartmentDto;
import uz.ttpu.inventory.references.api.EmployeeDto;
import uz.ttpu.inventory.references.api.FloorDto;
import uz.ttpu.inventory.references.api.ReferenceSyncApi;
import uz.ttpu.inventory.references.api.RoomDto;

/**
 * Prompt 10 (M7 Pilot & Handover): Pilot Building 1C XLSX Data Migration Service. Reads pilot
 * building asset export sheets from 1C Accounting (.xlsx), validates rows, flags reference/data
 * mismatches (e.g., missing rooms, missing employees, missing categories/buildings), and safely
 * maps valid rows to the {@code references} and {@code asset} tables via established entity logic.
 */
@Service
@RequiredArgsConstructor
public class PilotDataMigrationService {

  public static final String MISMATCH_MISSING_BUILDING = "MISSING_BUILDING";
  public static final String MISMATCH_MISSING_FLOOR = "MISSING_FLOOR";
  public static final String MISMATCH_MISSING_ROOM = "MISSING_ROOM";
  public static final String MISMATCH_MISSING_DEPARTMENT = "MISSING_DEPARTMENT";
  public static final String MISMATCH_MISSING_EMPLOYEE = "MISSING_EMPLOYEE";
  public static final String MISMATCH_MISSING_CATEGORY = "MISSING_CATEGORY";
  public static final String MISMATCH_INVALID_ROW_DATA = "INVALID_ROW_DATA";
  public static final String MISMATCH_DUPLICATE_INVENTORY_NUMBER = "DUPLICATE_INVENTORY_NUMBER";

  private final ReferenceSyncApi referenceSyncApi;
  private final AssetLogisticsApi assetLogisticsApi;

  @Transactional
  public PilotMigrationResultDto importPilotBuildingXlsx(PilotMigrationImportRequest request) {
    if (request == null || request.xlsxBase64() == null || request.xlsxBase64().isBlank()) {
      throw new IllegalArgumentException("xlsxBase64 payload must not be blank");
    }
    byte[] xlsxBytes;
    try {
      xlsxBytes = Base64.getDecoder().decode(request.xlsxBase64().trim());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid Base64 encoding for 1C pilot XLSX payload", ex);
    }
    return importPilotBuildingXlsxBytes(
        xlsxBytes,
        request.defaultBuildingCode(),
        request.dryRun(),
        request.strictMode(),
        request.autoCreateMissingCategories());
  }

  @Transactional
  public PilotMigrationResultDto importPilotBuildingXlsxBytes(
      byte[] xlsxBytes,
      String defaultBuildingCode,
      boolean dryRun,
      boolean strictMode,
      boolean autoCreateMissingCategories) {
    if (xlsxBytes == null || xlsxBytes.length == 0) {
      throw new IllegalArgumentException("XLSX binary payload must not be empty");
    }

    List<PilotMigrationMismatchDto> allMismatches = new ArrayList<>();
    List<PilotMigrationRowResultDto> rowResults = new ArrayList<>();
    Set<String> seenInventoryNumbers = new HashSet<>();

    int totalRowsRead = 0;
    int validRowsCount = 0;
    int importedItemsCount = 0;
    int skippedRowsCount = 0;

    DataFormatter formatter = new DataFormatter();

    try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
      if (workbook.getNumberOfSheets() == 0) {
        throw new IllegalArgumentException("1C pilot XLSX workbook contains no sheets");
      }
      Sheet sheet = workbook.getSheetAt(0);
      int lastRowNum = sheet.getLastRowNum();

      // Row 0 is header row; data starts at row 1 (Excel row 2)
      for (int rowIdx = 1; rowIdx <= lastRowNum; rowIdx++) {
        Row row = sheet.getRow(rowIdx);
        if (row == null || isRowCompletelyBlank(row, formatter)) {
          continue;
        }

        totalRowsRead++;
        int excelRowNumber = rowIdx + 1;
        List<PilotMigrationMismatchDto> rowMismatches = new ArrayList<>();

        // Standard 1C Pilot Export Columns:
        // 0: Inventory Number | 1: Asset Name | 2: Serial Number | 3: Model
        // 4: Category Code    | 5: Category Name | 6: Building Code | 7: Floor Number
        // 8: Room Number      | 9: Department Code | 10: Employee Code | 11: Employee Full Name
        // 12: Purchase Price  | 13: Residual Value | 14: 1C Ref ID
        String inventoryNumber = readCell(row, 0, formatter);
        String assetName = readCell(row, 1, formatter);
        String serialNumber = readCell(row, 2, formatter);
        String model = readCell(row, 3, formatter);
        String categoryCode = readCell(row, 4, formatter);
        String categoryName = readCell(row, 5, formatter);
        String buildingCode = readCell(row, 6, formatter);
        if ((buildingCode == null || buildingCode.isBlank())
            && defaultBuildingCode != null
            && !defaultBuildingCode.isBlank()) {
          buildingCode = defaultBuildingCode.trim();
        }
        String floorNumberRaw = readCell(row, 7, formatter);
        String roomNumber = readCell(row, 8, formatter);
        String departmentCode = readCell(row, 9, formatter);
        String employeeCode = readCell(row, 10, formatter);
        String employeeFullName = readCell(row, 11, formatter);
        String purchasePriceRaw = readCell(row, 12, formatter);
        String residualValueRaw = readCell(row, 13, formatter);
        String oneCRefId = readCell(row, 14, formatter);

        boolean fatalRowError = false;

        if (inventoryNumber == null || inventoryNumber.isBlank()) {
          rowMismatches.add(
              new PilotMigrationMismatchDto(
                  excelRowNumber,
                  "",
                  MISMATCH_INVALID_ROW_DATA,
                  "Inventory Number",
                  "",
                  "Inventory Number (col 1) is required in 1C export"));
          fatalRowError = true;
        } else if (!seenInventoryNumbers.add(inventoryNumber.toUpperCase())) {
          rowMismatches.add(
              new PilotMigrationMismatchDto(
                  excelRowNumber,
                  inventoryNumber,
                  MISMATCH_DUPLICATE_INVENTORY_NUMBER,
                  "Inventory Number",
                  inventoryNumber,
                  "Duplicate inventoryNumber within the same 1C pilot XLSX sheet: "
                      + inventoryNumber));
          fatalRowError = true;
        }

        if (assetName == null || assetName.isBlank()) {
          rowMismatches.add(
              new PilotMigrationMismatchDto(
                  excelRowNumber,
                  safeStr(inventoryNumber),
                  MISMATCH_INVALID_ROW_DATA,
                  "Asset Name",
                  "",
                  "Asset Name (col 2) is required"));
          fatalRowError = true;
        }

        BigDecimal purchasePrice =
            parsePrice(
                purchasePriceRaw,
                excelRowNumber,
                safeStr(inventoryNumber),
                "Purchase Price",
                rowMismatches);
        BigDecimal residualValue =
            parsePrice(
                residualValueRaw,
                excelRowNumber,
                safeStr(inventoryNumber),
                "Residual Value",
                rowMismatches);
        if (purchasePrice == null || residualValue == null) {
          fatalRowError = true;
        } else if (residualValue.compareTo(purchasePrice) > 0) {
          rowMismatches.add(
              new PilotMigrationMismatchDto(
                  excelRowNumber,
                  safeStr(inventoryNumber),
                  MISMATCH_INVALID_ROW_DATA,
                  "Residual Value",
                  residualValueRaw,
                  "Residual value ("
                      + residualValue
                      + ") cannot exceed purchase price ("
                      + purchasePrice
                      + ")"));
          fatalRowError = true;
        }

        // Resolve Category
        CategoryDto category = null;
        if (categoryCode == null || categoryCode.isBlank()) {
          rowMismatches.add(
              new PilotMigrationMismatchDto(
                  excelRowNumber,
                  safeStr(inventoryNumber),
                  MISMATCH_MISSING_CATEGORY,
                  "Category Code",
                  "",
                  "Category Code (col 5) is missing"));
          fatalRowError = true;
        } else {
          Optional<CategoryDto> foundCat = referenceSyncApi.findCategoryByCode(categoryCode);
          if (foundCat.isPresent()) {
            category = foundCat.get();
          } else if (autoCreateMissingCategories && !dryRun) {
            category = referenceSyncApi.ensurePilotCategory(categoryCode, categoryName, 5);
          } else {
            rowMismatches.add(
                new PilotMigrationMismatchDto(
                    excelRowNumber,
                    safeStr(inventoryNumber),
                    MISMATCH_MISSING_CATEGORY,
                    "Category Code",
                    categoryCode,
                    "Category not found in reference table: " + categoryCode));
            fatalRowError = true;
          }
        }

        // Resolve Building
        BuildingDto building = null;
        if (buildingCode != null && !buildingCode.isBlank()) {
          Optional<BuildingDto> foundBldg = referenceSyncApi.findBuildingByCode(buildingCode);
          if (foundBldg.isPresent()) {
            building = foundBldg.get();
          } else {
            rowMismatches.add(
                new PilotMigrationMismatchDto(
                    excelRowNumber,
                    safeStr(inventoryNumber),
                    MISMATCH_MISSING_BUILDING,
                    "Building Code",
                    buildingCode,
                    "Pilot building code not found in buildings table: " + buildingCode));
          }
        }

        // Resolve Floor
        FloorDto floor = null;
        if (floorNumberRaw != null && !floorNumberRaw.isBlank() && building != null) {
          try {
            int floorNum = Integer.parseInt(floorNumberRaw.trim());
            Optional<FloorDto> foundFloor =
                referenceSyncApi.findFloorByBuildingAndNumber(building.id(), floorNum);
            if (foundFloor.isPresent()) {
              floor = foundFloor.get();
            } else {
              rowMismatches.add(
                  new PilotMigrationMismatchDto(
                      excelRowNumber,
                      safeStr(inventoryNumber),
                      MISMATCH_MISSING_FLOOR,
                      "Floor Number",
                      floorNumberRaw,
                      "Floor " + floorNum + " not found in building " + building.code()));
            }
          } catch (NumberFormatException ex) {
            rowMismatches.add(
                new PilotMigrationMismatchDto(
                    excelRowNumber,
                    safeStr(inventoryNumber),
                    MISMATCH_INVALID_ROW_DATA,
                    "Floor Number",
                    floorNumberRaw,
                    "Floor Number is not a valid integer: " + floorNumberRaw));
          }
        }

        // Resolve Room (Flag mismatch if room is specified in 1C export but missing in reference
        // table)
        RoomDto room = null;
        if (roomNumber != null && !roomNumber.isBlank()) {
          Optional<RoomDto> foundRoom =
              referenceSyncApi.findRoomByBuildingAndRoomNumber(
                  building != null ? building.id() : null, roomNumber);
          if (foundRoom.isPresent()) {
            room = foundRoom.get();
          } else {
            rowMismatches.add(
                new PilotMigrationMismatchDto(
                    excelRowNumber,
                    safeStr(inventoryNumber),
                    MISMATCH_MISSING_ROOM,
                    "Room Number",
                    roomNumber,
                    "Room '"
                        + roomNumber
                        + "' referenced in 1C export does not exist in rooms reference table"));
          }
        }

        // Resolve Department
        DepartmentDto department = null;
        if (departmentCode != null && !departmentCode.isBlank()) {
          Optional<DepartmentDto> foundDept = referenceSyncApi.findDepartmentByCode(departmentCode);
          if (foundDept.isPresent()) {
            department = foundDept.get();
          } else {
            rowMismatches.add(
                new PilotMigrationMismatchDto(
                    excelRowNumber,
                    safeStr(inventoryNumber),
                    MISMATCH_MISSING_DEPARTMENT,
                    "Department Code",
                    departmentCode,
                    "Department '"
                        + departmentCode
                        + "' referenced in 1C export does not exist in departments table"));
          }
        }

        // Resolve Responsible Employee (Flag mismatch if employee is specified in 1C export but
        // missing in employees table)
        EmployeeDto custodian = null;
        if ((employeeCode != null && !employeeCode.isBlank())
            || (employeeFullName != null && !employeeFullName.isBlank())) {
          Optional<EmployeeDto> foundEmp =
              referenceSyncApi.findEmployeeByCodeOrFullName(employeeCode, employeeFullName);
          if (foundEmp.isPresent()) {
            custodian = foundEmp.get();
          } else {
            String rawEmpRef =
                (employeeCode != null && !employeeCode.isBlank() ? employeeCode : "")
                    + (employeeFullName != null && !employeeFullName.isBlank()
                        ? " (" + employeeFullName + ")"
                        : "");
            rowMismatches.add(
                new PilotMigrationMismatchDto(
                    excelRowNumber,
                    safeStr(inventoryNumber),
                    MISMATCH_MISSING_EMPLOYEE,
                    "Responsible Employee",
                    rawEmpRef.trim(),
                    "Responsible employee '"
                        + rawEmpRef.trim()
                        + "' referenced in 1C export does not exist in employees table"));
          }
        }

        allMismatches.addAll(rowMismatches);

        boolean rowValid = !fatalRowError && (!strictMode || rowMismatches.isEmpty());
        if (rowValid) {
          validRowsCount++;
        }

        if (!rowValid || dryRun) {
          skippedRowsCount++;
          rowResults.add(
              new PilotMigrationRowResultDto(
                  excelRowNumber,
                  safeStr(inventoryNumber),
                  safeStr(assetName),
                  rowValid,
                  false,
                  null,
                  null,
                  rowMismatches));
          continue;
        }

        Map<String, Object> customAttrs = new LinkedHashMap<>();
        customAttrs.put("migratedFrom", "1C_PILOT_XLSX");
        if (model != null && !model.isBlank()) {
          customAttrs.put("model", model);
        }

        String roomLabel =
            room != null
                ? (room.roomNumber() + (room.name() != null ? " " + room.name() : "")).trim()
                : (roomNumber != null && !roomNumber.isBlank() ? roomNumber.trim() : null);

        String responsiblePersonLabel =
            custodian != null
                ? (custodian.firstName()
                        + " "
                        + custodian.lastName()
                        + " ("
                        + custodian.employeeCode()
                        + ")")
                    .trim()
                : (employeeFullName != null && !employeeFullName.isBlank()
                    ? employeeFullName.trim()
                    : null);

        ItemDto savedItem =
            assetLogisticsApi.upsertPilotMigratedItem(
                new PilotItemUpsertRequest(
                    inventoryNumber.trim(),
                    assetName.trim(),
                    serialNumber != null && !serialNumber.isBlank() ? serialNumber.trim() : null,
                    model != null && !model.isBlank() ? model.trim() : null,
                    category.id(),
                    building != null ? building.id() : null,
                    floor != null ? floor.id() : (room != null ? room.floorId() : null),
                    room != null ? room.id() : null,
                    roomLabel,
                    department != null
                        ? department.id()
                        : (room != null ? room.departmentId() : null),
                    custodian != null ? custodian.id() : null,
                    responsiblePersonLabel,
                    purchasePrice,
                    residualValue,
                    Instant.now(),
                    oneCRefId != null && !oneCRefId.isBlank() ? oneCRefId.trim() : null,
                    customAttrs));

        importedItemsCount++;
        rowResults.add(
            new PilotMigrationRowResultDto(
                excelRowNumber,
                savedItem.inventoryNumber(),
                savedItem.name(),
                true,
                true,
                savedItem.id(),
                savedItem.qrCode(),
                rowMismatches));
      }
    } catch (IOException ex) {
      throw new IllegalArgumentException(
          "Failed to read 1C pilot XLSX workbook: " + ex.getMessage(), ex);
    }

    return new PilotMigrationResultDto(
        totalRowsRead,
        validRowsCount,
        importedItemsCount,
        skippedRowsCount,
        allMismatches.size(),
        dryRun,
        strictMode,
        Instant.now(),
        allMismatches,
        rowResults);
  }

  public byte[] generateSamplePilot1CXlsxTemplate() {
    try (Workbook wb = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = wb.createSheet("1C_Pilot_Export");
      String[] headers = {
        "Inventory Number", "Asset Name", "Serial Number", "Model",
        "Category Code", "Category Name", "Building Code", "Floor Number",
        "Room Number", "Department Code", "Employee Code", "Employee Full Name",
        "Purchase Price", "Residual Value", "1C Ref ID"
      };
      Row headerRow = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        headerRow.createCell(i).setCellValue(headers[i]);
      }

      Row sampleRow = sheet.createRow(1);
      sampleRow.createCell(0).setCellValue("TTPU-PILOT-00101");
      sampleRow.createCell(1).setCellValue("Dell Precision 3660 Tower Workstation");
      sampleRow.createCell(2).setCellValue("SN-PILOT-3660-01");
      sampleRow.createCell(3).setCellValue("Precision 3660");
      sampleRow.createCell(4).setCellValue("CAT-IT");
      sampleRow.createCell(5).setCellValue("IT & Computing Equipment");
      sampleRow.createCell(6).setCellValue("B-MAIN");
      sampleRow.createCell(7).setCellValue("2");
      sampleRow.createCell(8).setCellValue("204");
      sampleRow.createCell(9).setCellValue("DEP-IT");
      sampleRow.createCell(10).setCellValue("EMP-001");
      sampleRow.createCell(11).setCellValue("Sardor Karimov");
      sampleRow.createCell(12).setCellValue("24000000.00");
      sampleRow.createCell(13).setCellValue("21600000.00");
      sampleRow.createCell(14).setCellValue("1C-PILOT-GUID-00101");

      wb.write(out);
      return out.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to generate sample 1C pilot XLSX template", ex);
    }
  }

  private static BigDecimal parsePrice(
      String raw,
      int excelRowNumber,
      String inventoryNumber,
      String columnName,
      List<PilotMigrationMismatchDto> mismatches) {
    if (raw == null || raw.isBlank()) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    String cleaned = raw.replace(",", ".").replaceAll("[^0-9.-]", "");
    if (cleaned.isEmpty() || "-".equals(cleaned)) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    try {
      BigDecimal val = new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP);
      if (val.compareTo(BigDecimal.ZERO) < 0) {
        mismatches.add(
            new PilotMigrationMismatchDto(
                excelRowNumber,
                inventoryNumber,
                MISMATCH_INVALID_ROW_DATA,
                columnName,
                raw,
                columnName + " must not be negative: " + raw));
        return null;
      }
      return val;
    } catch (NumberFormatException ex) {
      mismatches.add(
          new PilotMigrationMismatchDto(
              excelRowNumber,
              inventoryNumber,
              MISMATCH_INVALID_ROW_DATA,
              columnName,
              raw,
              columnName + " is not a valid decimal number: " + raw));
      return null;
    }
  }

  private static boolean isRowCompletelyBlank(Row row, DataFormatter formatter) {
    short lastCell = row.getLastCellNum();
    if (lastCell <= 0) {
      return true;
    }
    for (int i = 0; i < lastCell; i++) {
      String v = readCell(row, i, formatter);
      if (v != null && !v.isBlank()) {
        return false;
      }
    }
    return true;
  }

  private static String readCell(Row row, int cellIndex, DataFormatter formatter) {
    Cell cell = row.getCell(cellIndex);
    if (cell == null) {
      return null;
    }
    String formatted = formatter.formatCellValue(cell);
    return formatted != null ? formatted.trim() : null;
  }

  private static String safeStr(String val) {
    return val != null ? val.trim() : "";
  }
}
