package uz.ttpu.inventory.integration.service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.PilotItemUpsertRequest;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.integration.api.OneCImportCommitResultDto;
import uz.ttpu.inventory.integration.api.OneCImportConfirmRequest;
import uz.ttpu.inventory.integration.api.OneCImportPreviewRequest;
import uz.ttpu.inventory.integration.api.OneCImportPreviewResponseDto;
import uz.ttpu.inventory.integration.api.OneCImportRowPreviewDto;
import uz.ttpu.inventory.references.api.BuildingDto;
import uz.ttpu.inventory.references.api.CategoryDto;
import uz.ttpu.inventory.references.api.DepartmentDto;
import uz.ttpu.inventory.references.api.EmployeeDto;
import uz.ttpu.inventory.references.api.FloorDto;
import uz.ttpu.inventory.references.api.ReferenceSyncApi;
import uz.ttpu.inventory.references.api.RoomDto;

/**
 * Prompt 11 (B2.2): Initial 1C File Import Service (XLSX & CSV). Two-phase commit workflow: 1.
 * Creates an in-memory error preview without committing any transactions. 2. Flags duplicate
 * inventory & serial numbers (both in-file and database). 3. Flags missing rooms, employees,
 * categories, and departments. 4. Commits valid rows into asset & reference tables in a SINGLE
 * database transaction upon user confirmation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OneCImportService {

  public static final String ERROR_DUPLICATE_INVENTORY_IN_FILE = "DUPLICATE_INVENTORY_IN_FILE";
  public static final String ERROR_DUPLICATE_SERIAL_IN_FILE = "DUPLICATE_SERIAL_IN_FILE";
  public static final String ERROR_DUPLICATE_INVENTORY_IN_DB = "DUPLICATE_INVENTORY_IN_DB";
  public static final String ERROR_DUPLICATE_SERIAL_IN_DB = "DUPLICATE_SERIAL_IN_DB";
  public static final String ERROR_MISSING_ROOM = "MISSING_ROOM";
  public static final String ERROR_MISSING_EMPLOYEE = "MISSING_EMPLOYEE";
  public static final String ERROR_MISSING_CATEGORY = "MISSING_CATEGORY";
  public static final String ERROR_MISSING_BUILDING = "MISSING_BUILDING";
  public static final String ERROR_MISSING_FLOOR = "MISSING_FLOOR";
  public static final String ERROR_MISSING_DEPARTMENT = "MISSING_DEPARTMENT";
  public static final String ERROR_INVALID_DATA = "INVALID_DATA";

  private final ReferenceSyncApi referenceSyncApi;
  private final AssetLogisticsApi assetLogisticsApi;

  // In-memory staging storage for Two-Phase commit preview
  private final Map<UUID, StagedSession> stagedSessions = new ConcurrentHashMap<>();

  public record StagedSession(
      UUID stagingId,
      String fileName,
      String fileType,
      String defaultBuildingCode,
      boolean autoCreateCategories,
      List<RawRowData> rows,
      OneCImportPreviewResponseDto previewResponse,
      Instant stagedAt,
      Instant expiresAt,
      boolean committed) {
    public StagedSession markCommitted() {
      return new StagedSession(
          stagingId,
          fileName,
          fileType,
          defaultBuildingCode,
          autoCreateCategories,
          rows,
          previewResponse,
          stagedAt,
          expiresAt,
          true);
    }
  }

  public record RawRowData(
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
      List<String> errors,
      List<String> warnings) {}

  public OneCImportPreviewResponseDto createPreview(OneCImportPreviewRequest request) {
    if (request == null
        || request.fileContentBase64() == null
        || request.fileContentBase64().isBlank()) {
      throw new IllegalArgumentException("fileContentBase64 must not be blank");
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(request.fileContentBase64().trim());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid Base64 encoding for 1C import file payload", ex);
    }

    String fileName =
        request.fileName() != null && !request.fileName().isBlank()
            ? request.fileName().trim()
            : "1c-import.xlsx";

    return createPreviewFromBytes(
        bytes, fileName, request.defaultBuildingCode(), request.autoCreateMissingCategories());
  }

  public OneCImportPreviewResponseDto createPreviewFromBytes(
      byte[] fileBytes,
      String fileName,
      String defaultBuildingCode,
      boolean autoCreateMissingCategories) {
    if (fileBytes == null || fileBytes.length == 0) {
      throw new IllegalArgumentException("Uploaded 1C file payload is empty");
    }

    boolean isCsv = isCsvFormat(fileName, fileBytes);
    String fileType = isCsv ? "CSV" : "XLSX";

    List<RawRowData> rawRows =
        isCsv
            ? parseCsv(fileBytes, defaultBuildingCode)
            : parseXlsx(fileBytes, defaultBuildingCode);

    // Analyze and detect duplicates and missing references
    List<RawRowData> evaluatedRows = evaluateRows(rawRows, autoCreateMissingCategories);

    UUID stagingId = UUID.randomUUID();
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(3600); // 1-hour preview expiration

    List<OneCImportRowPreviewDto> rowPreviews = new ArrayList<>();
    List<String> dupInvs = new ArrayList<>();
    List<String> dupSerials = new ArrayList<>();
    List<String> missingRooms = new ArrayList<>();
    List<String> missingEmps = new ArrayList<>();
    List<String> missingCats = new ArrayList<>();
    List<String> missingBldgs = new ArrayList<>();

    int validCount = 0;
    int errorCount = 0;
    int warningCount = 0;

    for (RawRowData row : evaluatedRows) {
      boolean hasErrors = !row.errors().isEmpty();
      boolean hasDup = row.errors().stream().anyMatch(e -> e.startsWith("DUPLICATE_"));
      if (!hasErrors) {
        validCount++;
      } else {
        errorCount++;
      }
      if (!row.warnings().isEmpty()) {
        warningCount++;
      }

      for (String err : row.errors()) {
        if (err.startsWith(ERROR_DUPLICATE_INVENTORY_IN_FILE)
            || err.startsWith(ERROR_DUPLICATE_INVENTORY_IN_DB)) {
          dupInvs.add("Row " + row.rowNumber() + ": " + row.inventoryNumber());
        } else if (err.startsWith(ERROR_DUPLICATE_SERIAL_IN_FILE)
            || err.startsWith(ERROR_DUPLICATE_SERIAL_IN_DB)) {
          dupSerials.add("Row " + row.rowNumber() + ": " + row.serialNumber());
        } else if (err.startsWith(ERROR_MISSING_ROOM)) {
          missingRooms.add("Row " + row.rowNumber() + ": room '" + row.roomNumber() + "'");
        } else if (err.startsWith(ERROR_MISSING_EMPLOYEE)) {
          missingEmps.add(
              "Row "
                  + row.rowNumber()
                  + ": employee '"
                  + row.employeeCode()
                  + " / "
                  + row.employeeFullName()
                  + "'");
        } else if (err.startsWith(ERROR_MISSING_CATEGORY)) {
          missingCats.add("Row " + row.rowNumber() + ": category '" + row.categoryCode() + "'");
        } else if (err.startsWith(ERROR_MISSING_BUILDING)) {
          missingBldgs.add("Row " + row.rowNumber() + ": building '" + row.buildingCode() + "'");
        }
      }

      rowPreviews.add(
          new OneCImportRowPreviewDto(
              row.rowNumber(),
              row.inventoryNumber(),
              row.assetName(),
              row.serialNumber(),
              row.model(),
              row.categoryCode(),
              row.categoryName(),
              row.buildingCode(),
              row.floorNumber(),
              row.roomNumber(),
              row.departmentCode(),
              row.employeeCode(),
              row.employeeFullName(),
              row.purchasePrice(),
              row.residualValue(),
              row.oneCRefId(),
              row.valid(),
              hasDup,
              row.errors(),
              row.warnings()));
    }

    boolean canCommit = validCount > 0;

    OneCImportPreviewResponseDto response =
        new OneCImportPreviewResponseDto(
            stagingId,
            fileName,
            fileType,
            evaluatedRows.size(),
            validCount,
            errorCount,
            warningCount,
            canCommit,
            dupInvs,
            dupSerials,
            missingRooms,
            missingEmps,
            missingCats,
            missingBldgs,
            rowPreviews,
            now,
            expiresAt);

    // Store preview in-memory (NO database changes committed)
    stagedSessions.put(
        stagingId,
        new StagedSession(
            stagingId,
            fileName,
            fileType,
            defaultBuildingCode,
            autoCreateMissingCategories,
            evaluatedRows,
            response,
            now,
            expiresAt,
            false));

    log.info(
        "Created 1C import preview stagingId={} for file '{}' (total={}, valid={}, errors={})",
        stagingId,
        fileName,
        evaluatedRows.size(),
        validCount,
        errorCount);

    return response;
  }

  public OneCImportPreviewResponseDto getStagedPreview(UUID stagingId) {
    StagedSession session = stagedSessions.get(stagingId);
    if (session == null) {
      throw new ResourceNotFoundException("1C Import Staging Session", stagingId);
    }
    if (Instant.now().isAfter(session.expiresAt())) {
      stagedSessions.remove(stagingId);
      throw new IllegalStateException(
          "1C Import Staging Session " + stagingId + " has expired. Please re-upload.");
    }
    return session.previewResponse();
  }

  public void cancelStagedImport(UUID stagingId) {
    StagedSession removed = stagedSessions.remove(stagingId);
    if (removed == null) {
      throw new ResourceNotFoundException("1C Import Staging Session", stagingId);
    }
    log.info("Cancelled 1C import staging session {}", stagingId);
  }

  @Transactional
  public OneCImportCommitResultDto confirmAndCommitImport(
      UUID stagingId, OneCImportConfirmRequest request) {
    StagedSession session = stagedSessions.get(stagingId);
    if (session == null) {
      throw new ResourceNotFoundException("1C Import Staging Session", stagingId);
    }
    if (session.committed()) {
      throw new IllegalStateException(
          "Staging session " + stagingId + " has already been committed");
    }
    if (Instant.now().isAfter(session.expiresAt())) {
      stagedSessions.remove(stagingId);
      throw new IllegalStateException(
          "Staging session " + stagingId + " has expired. Please create a new preview.");
    }

    boolean skipInvalid = request != null && request.skipInvalidRows();
    boolean autoCreateCat =
        (request != null && request.autoCreateMissingCategories())
            || session.autoCreateCategories();

    int importedCount = 0;
    int skippedCount = 0;
    List<String> importedInvs = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    // Execute in single transaction
    for (RawRowData row : session.rows()) {
      if (!row.valid()) {
        if (!skipInvalid) {
          throw new IllegalStateException(
              "Cannot commit import with invalid rows. Row "
                  + row.rowNumber()
                  + " has errors: "
                  + row.errors()
                  + ". Enable skipInvalidRows to import valid rows only.");
        }
        skippedCount++;
        errors.add(
            "Skipped row " + row.rowNumber() + " (" + row.inventoryNumber() + "): " + row.errors());
        continue;
      }

      try {
        commitSingleRow(row, autoCreateCat);
        importedCount++;
        importedInvs.add(row.inventoryNumber());
      } catch (Exception ex) {
        log.error("Failed to persist row {}: {}", row.rowNumber(), ex.getMessage(), ex);
        if (!skipInvalid) {
          throw new IllegalStateException(
              "Commit aborted due to failure on row " + row.rowNumber() + ": " + ex.getMessage(),
              ex);
        }
        skippedCount++;
        errors.add("Failed row " + row.rowNumber() + ": " + ex.getMessage());
      }
    }

    stagedSessions.put(stagingId, session.markCommitted());

    log.info(
        "Committed 1C import stagingId={} in single transaction (imported={}, skipped={})",
        stagingId,
        importedCount,
        skippedCount);

    return new OneCImportCommitResultDto(
        stagingId,
        "COMMITTED",
        session.rows().size(),
        importedCount,
        skippedCount,
        Instant.now(),
        importedInvs,
        errors);
  }

  private void commitSingleRow(RawRowData row, boolean autoCreateCat) {
    CategoryDto category = null;
    if (row.categoryCode() != null && !row.categoryCode().isBlank()) {
      Optional<CategoryDto> catOpt = referenceSyncApi.findCategoryByCode(row.categoryCode());
      if (catOpt.isPresent()) {
        category = catOpt.get();
      } else if (autoCreateCat) {
        category = referenceSyncApi.ensurePilotCategory(row.categoryCode(), row.categoryName(), 5);
      }
    }
    if (category == null) {
      throw new IllegalArgumentException(
          "Cannot commit row "
              + row.rowNumber()
              + ": Category not resolved for code "
              + row.categoryCode());
    }

    BuildingDto building = null;
    if (row.buildingCode() != null && !row.buildingCode().isBlank()) {
      building = referenceSyncApi.findBuildingByCode(row.buildingCode()).orElse(null);
    }

    FloorDto floor = null;
    if (building != null && row.floorNumber() != null && !row.floorNumber().isBlank()) {
      try {
        int fNum = Integer.parseInt(row.floorNumber().trim());
        floor = referenceSyncApi.findFloorByBuildingAndNumber(building.id(), fNum).orElse(null);
      } catch (NumberFormatException ignored) {
      }
    }

    RoomDto room = null;
    if (row.roomNumber() != null && !row.roomNumber().isBlank()) {
      room =
          referenceSyncApi
              .findRoomByBuildingAndRoomNumber(
                  building != null ? building.id() : null, row.roomNumber())
              .orElse(null);
    }

    DepartmentDto dept = null;
    if (row.departmentCode() != null && !row.departmentCode().isBlank()) {
      dept = referenceSyncApi.findDepartmentByCode(row.departmentCode()).orElse(null);
    }

    EmployeeDto custodian = null;
    if ((row.employeeCode() != null && !row.employeeCode().isBlank())
        || (row.employeeFullName() != null && !row.employeeFullName().isBlank())) {
      custodian =
          referenceSyncApi
              .findEmployeeByCodeOrFullName(row.employeeCode(), row.employeeFullName())
              .orElse(null);
    }

    String roomLabel =
        room != null
            ? (room.roomNumber() + (room.name() != null ? " " + room.name() : "")).trim()
            : (row.roomNumber() != null && !row.roomNumber().isBlank()
                ? row.roomNumber().trim()
                : null);

    String responsiblePersonLabel =
        custodian != null
            ? (custodian.firstName()
                    + " "
                    + custodian.lastName()
                    + " ("
                    + custodian.employeeCode()
                    + ")")
                .trim()
            : (row.employeeFullName() != null && !row.employeeFullName().isBlank()
                ? row.employeeFullName().trim()
                : null);

    Map<String, Object> customAttrs = new LinkedHashMap<>();
    customAttrs.put("migratedFrom", "1C_B2_2_IMPORT");
    if (row.model() != null && !row.model().isBlank()) {
      customAttrs.put("model", row.model());
    }

    assetLogisticsApi.upsertPilotMigratedItem(
        new PilotItemUpsertRequest(
            row.inventoryNumber().trim(),
            row.assetName().trim(),
            row.serialNumber() != null && !row.serialNumber().isBlank()
                ? row.serialNumber().trim()
                : null,
            row.model() != null && !row.model().isBlank() ? row.model().trim() : null,
            category.id(),
            building != null ? building.id() : null,
            floor != null ? floor.id() : (room != null ? room.floorId() : null),
            room != null ? room.id() : null,
            roomLabel,
            dept != null ? dept.id() : (room != null ? room.departmentId() : null),
            custodian != null ? custodian.id() : null,
            responsiblePersonLabel,
            row.purchasePrice(),
            row.residualValue(),
            Instant.now(),
            row.oneCRefId() != null && !row.oneCRefId().isBlank() ? row.oneCRefId().trim() : null,
            customAttrs));
  }

  private List<RawRowData> evaluateRows(
      List<RawRowData> rows, boolean autoCreateMissingCategories) {
    List<RawRowData> evaluated = new ArrayList<>();
    Set<String> seenFileInvs = new HashSet<>();
    Set<String> seenFileSerials = new HashSet<>();

    for (RawRowData row : rows) {
      List<String> errors = new ArrayList<>(row.errors());
      List<String> warnings = new ArrayList<>(row.warnings());

      // Duplicate checks in file
      if (row.inventoryNumber() != null && !row.inventoryNumber().isBlank()) {
        String normInv = row.inventoryNumber().trim().toUpperCase(Locale.ROOT);
        if (!seenFileInvs.add(normInv)) {
          errors.add(
              ERROR_DUPLICATE_INVENTORY_IN_FILE
                  + ": Inventory number '"
                  + row.inventoryNumber()
                  + "' appears multiple times in uploaded file");
        }
        // Duplicate check in database
        if (assetLogisticsApi.existsByInventoryNumber(row.inventoryNumber())) {
          errors.add(
              ERROR_DUPLICATE_INVENTORY_IN_DB
                  + ": Inventory number '"
                  + row.inventoryNumber()
                  + "' already exists in database");
        }
      }

      if (row.serialNumber() != null && !row.serialNumber().isBlank()) {
        String normSerial = row.serialNumber().trim().toUpperCase(Locale.ROOT);
        if (!seenFileSerials.add(normSerial)) {
          errors.add(
              ERROR_DUPLICATE_SERIAL_IN_FILE
                  + ": Serial number '"
                  + row.serialNumber()
                  + "' appears multiple times in uploaded file");
        }
        if (assetLogisticsApi.existsBySerialNumber(row.serialNumber())) {
          errors.add(
              ERROR_DUPLICATE_SERIAL_IN_DB
                  + ": Serial number '"
                  + row.serialNumber()
                  + "' already exists in database");
        }
      }

      // Reference checks
      BuildingDto bldg = null;
      if (row.buildingCode() != null && !row.buildingCode().isBlank()) {
        Optional<BuildingDto> bOpt = referenceSyncApi.findBuildingByCode(row.buildingCode());
        if (bOpt.isPresent()) {
          bldg = bOpt.get();
        } else {
          errors.add(
              ERROR_MISSING_BUILDING
                  + ": Building '"
                  + row.buildingCode()
                  + "' does not exist in references");
        }
      }

      if (row.roomNumber() != null && !row.roomNumber().isBlank()) {
        Optional<RoomDto> rOpt =
            referenceSyncApi.findRoomByBuildingAndRoomNumber(
                bldg != null ? bldg.id() : null, row.roomNumber());
        if (rOpt.isEmpty()) {
          errors.add(
              ERROR_MISSING_ROOM
                  + ": Room '"
                  + row.roomNumber()
                  + "' does not exist in reference table");
        }
      }

      if ((row.employeeCode() != null && !row.employeeCode().isBlank())
          || (row.employeeFullName() != null && !row.employeeFullName().isBlank())) {
        Optional<EmployeeDto> eOpt =
            referenceSyncApi.findEmployeeByCodeOrFullName(
                row.employeeCode(), row.employeeFullName());
        if (eOpt.isEmpty()) {
          String label =
              (row.employeeCode() != null ? row.employeeCode() : "")
                  + " "
                  + (row.employeeFullName() != null ? row.employeeFullName() : "");
          errors.add(
              ERROR_MISSING_EMPLOYEE
                  + ": Responsible employee '"
                  + label.trim()
                  + "' does not exist in employees table");
        }
      }

      if (row.categoryCode() != null && !row.categoryCode().isBlank()) {
        Optional<CategoryDto> cOpt = referenceSyncApi.findCategoryByCode(row.categoryCode());
        if (cOpt.isEmpty()) {
          if (autoCreateMissingCategories) {
            warnings.add(
                "Category '" + row.categoryCode() + "' will be auto-created upon confirmed commit");
          } else {
            errors.add(
                ERROR_MISSING_CATEGORY
                    + ": Category code '"
                    + row.categoryCode()
                    + "' not found in reference table");
          }
        }
      }

      boolean valid = errors.isEmpty();
      evaluated.add(
          new RawRowData(
              row.rowNumber(),
              row.inventoryNumber(),
              row.assetName(),
              row.serialNumber(),
              row.model(),
              row.categoryCode(),
              row.categoryName(),
              row.buildingCode(),
              row.floorNumber(),
              row.roomNumber(),
              row.departmentCode(),
              row.employeeCode(),
              row.employeeFullName(),
              row.purchasePrice(),
              row.residualValue(),
              row.oneCRefId(),
              valid,
              errors,
              warnings));
    }
    return evaluated;
  }

  private List<RawRowData> parseXlsx(byte[] bytes, String defaultBuildingCode) {
    List<RawRowData> list = new ArrayList<>();
    DataFormatter formatter = new DataFormatter();

    try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      if (wb.getNumberOfSheets() == 0) {
        throw new IllegalArgumentException("XLSX workbook contains no sheets");
      }
      Sheet sheet = wb.getSheetAt(0);
      int lastRowNum = sheet.getLastRowNum();

      for (int r = 1; r <= lastRowNum; r++) {
        Row row = sheet.getRow(r);
        if (row == null || isBlankRow(row, formatter)) {
          continue;
        }
        int rowNum = r + 1;
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String inv = readCell(row, 0, formatter);
        String name = readCell(row, 1, formatter);
        String serial = readCell(row, 2, formatter);
        String model = readCell(row, 3, formatter);
        String catCode = readCell(row, 4, formatter);
        String catName = readCell(row, 5, formatter);
        String bldg = readCell(row, 6, formatter);
        if ((bldg == null || bldg.isBlank()) && defaultBuildingCode != null) {
          bldg = defaultBuildingCode.trim();
        }
        String floor = readCell(row, 7, formatter);
        String room = readCell(row, 8, formatter);
        String dept = readCell(row, 9, formatter);
        String empCode = readCell(row, 10, formatter);
        String empName = readCell(row, 11, formatter);
        String priceRaw = readCell(row, 12, formatter);
        String residRaw = readCell(row, 13, formatter);
        String refId = readCell(row, 14, formatter);

        if (inv == null || inv.isBlank()) {
          errors.add(ERROR_INVALID_DATA + ": Inventory number is required");
        }
        if (name == null || name.isBlank()) {
          errors.add(ERROR_INVALID_DATA + ": Asset name is required");
        }

        BigDecimal purchasePrice = parsePrice(priceRaw, errors, "Purchase Price");
        BigDecimal residualValue = parsePrice(residRaw, errors, "Residual Value");
        if (purchasePrice != null
            && residualValue != null
            && residualValue.compareTo(purchasePrice) > 0) {
          errors.add(
              ERROR_INVALID_DATA
                  + ": Residual value ("
                  + residualValue
                  + ") cannot exceed purchase price ("
                  + purchasePrice
                  + ")");
        }

        list.add(
            new RawRowData(
                rowNum,
                inv,
                name,
                serial,
                model,
                catCode,
                catName,
                bldg,
                floor,
                room,
                dept,
                empCode,
                empName,
                purchasePrice != null ? purchasePrice : BigDecimal.ZERO,
                residualValue != null ? residualValue : BigDecimal.ZERO,
                refId,
                errors.isEmpty(),
                errors,
                warnings));
      }
    } catch (IOException ex) {
      throw new IllegalArgumentException("Failed to parse XLSX file: " + ex.getMessage(), ex);
    }
    return list;
  }

  private List<RawRowData> parseCsv(byte[] bytes, String defaultBuildingCode) {
    List<RawRowData> list = new ArrayList<>();
    String content = new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", ""); // Remove BOM
    try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        return list;
      }
      char delimiter = detectDelimiter(headerLine);
      String line;
      int r = 1;
      while ((line = reader.readLine()) != null) {
        r++;
        if (line.trim().isEmpty()) {
          continue;
        }
        List<String> tokens = parseCsvLine(line, delimiter);
        if (tokens.isEmpty()) {
          continue;
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String inv = getToken(tokens, 0);
        String name = getToken(tokens, 1);
        String serial = getToken(tokens, 2);
        String model = getToken(tokens, 3);
        String catCode = getToken(tokens, 4);
        String catName = getToken(tokens, 5);
        String bldg = getToken(tokens, 6);
        if ((bldg == null || bldg.isBlank()) && defaultBuildingCode != null) {
          bldg = defaultBuildingCode.trim();
        }
        String floor = getToken(tokens, 7);
        String room = getToken(tokens, 8);
        String dept = getToken(tokens, 9);
        String empCode = getToken(tokens, 10);
        String empName = getToken(tokens, 11);
        String priceRaw = getToken(tokens, 12);
        String residRaw = getToken(tokens, 13);
        String refId = getToken(tokens, 14);

        if (inv == null || inv.isBlank()) {
          errors.add(ERROR_INVALID_DATA + ": Inventory number is required");
        }
        if (name == null || name.isBlank()) {
          errors.add(ERROR_INVALID_DATA + ": Asset name is required");
        }

        BigDecimal purchasePrice = parsePrice(priceRaw, errors, "Purchase Price");
        BigDecimal residualValue = parsePrice(residRaw, errors, "Residual Value");
        if (purchasePrice != null
            && residualValue != null
            && residualValue.compareTo(purchasePrice) > 0) {
          errors.add(
              ERROR_INVALID_DATA
                  + ": Residual value ("
                  + residualValue
                  + ") cannot exceed purchase price ("
                  + purchasePrice
                  + ")");
        }

        list.add(
            new RawRowData(
                r,
                inv,
                name,
                serial,
                model,
                catCode,
                catName,
                bldg,
                floor,
                room,
                dept,
                empCode,
                empName,
                purchasePrice != null ? purchasePrice : BigDecimal.ZERO,
                residualValue != null ? residualValue : BigDecimal.ZERO,
                refId,
                errors.isEmpty(),
                errors,
                warnings));
      }
    } catch (IOException ex) {
      throw new IllegalArgumentException("Failed to parse CSV file: " + ex.getMessage(), ex);
    }
    return list;
  }

  private static char detectDelimiter(String line) {
    int commas = countChars(line, ',');
    int semicolons = countChars(line, ';');
    int tabs = countChars(line, '\t');
    if (semicolons > commas && semicolons > tabs) return ';';
    if (tabs > commas && tabs > semicolons) return '\t';
    return ',';
  }

  private static int countChars(String s, char c) {
    int count = 0;
    for (char ch : s.toCharArray()) {
      if (ch == c) count++;
    }
    return count;
  }

  private static List<String> parseCsvLine(String line, char delimiter) {
    List<String> tokens = new ArrayList<>();
    StringBuilder sb = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          sb.append('"');
          i++;
        } else {
          inQuotes = !inQuotes;
        }
      } else if (c == delimiter && !inQuotes) {
        tokens.add(sb.toString().trim());
        sb.setLength(0);
      } else {
        sb.append(c);
      }
    }
    tokens.add(sb.toString().trim());
    return tokens;
  }

  private static String getToken(List<String> tokens, int idx) {
    if (idx < tokens.size()) {
      String val = tokens.get(idx);
      return val != null && !val.isBlank() ? val.trim() : null;
    }
    return null;
  }

  private static boolean isCsvFormat(String fileName, byte[] bytes) {
    if (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
      return true;
    }
    // ZIP / XLSX magic number: PK\003\004 (0x50, 0x4B, 0x03, 0x04)
    if (bytes.length >= 4
        && bytes[0] == 0x50
        && bytes[1] == 0x4B
        && bytes[2] == 0x03
        && bytes[3] == 0x04) {
      return false;
    }
    return true;
  }

  private static boolean isBlankRow(Row row, DataFormatter formatter) {
    short last = row.getLastCellNum();
    if (last <= 0) return true;
    for (int i = 0; i < last; i++) {
      String v = readCell(row, i, formatter);
      if (v != null && !v.isBlank()) return false;
    }
    return true;
  }

  private static String readCell(Row row, int idx, DataFormatter formatter) {
    Cell cell = row.getCell(idx);
    if (cell == null) return null;
    String v = formatter.formatCellValue(cell);
    return v != null && !v.isBlank() ? v.trim() : null;
  }

  private static BigDecimal parsePrice(String raw, List<String> errors, String fieldName) {
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
        errors.add(ERROR_INVALID_DATA + ": " + fieldName + " cannot be negative: " + raw);
        return null;
      }
      return val;
    } catch (NumberFormatException ex) {
      errors.add(ERROR_INVALID_DATA + ": " + fieldName + " is not a valid decimal number: " + raw);
      return null;
    }
  }

  public byte[] generateSampleCsv() {
    String csv =
        """
Inventory Number,Asset Name,Serial Number,Model,Category Code,Category Name,Building Code,Floor Number,Room Number,Department Code,Employee Code,Employee Full Name,Purchase Price,Residual Value,1C Ref ID
TTPU-1C-00101,Dell Latitude 5530 Laptop,SN-LAT-5530-01,Latitude 5530,CAT-IT,IT Equipment,B-MAIN,2,204,DEP-IT,EMP-001,Sardor Karimov,18500000.00,16650000.00,1C-GUID-00101
TTPU-1C-00102,Epson EB-2250U Projector,SN-EPSON-2250-01,EB-2250U,CAT-AV,Audio-Visual,B-MAIN,1,101,DEP-ADMIN,EMP-002,Nodira Alimova,12000000.00,10800000.00,1C-GUID-00102
""";
    return csv.getBytes(StandardCharsets.UTF_8);
  }
}
