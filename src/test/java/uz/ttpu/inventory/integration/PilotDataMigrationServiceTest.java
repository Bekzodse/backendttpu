package uz.ttpu.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.asset.api.PilotItemUpsertRequest;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.integration.api.PilotMigrationImportRequest;
import uz.ttpu.inventory.integration.api.PilotMigrationResultDto;
import uz.ttpu.inventory.integration.service.PilotDataMigrationService;
import uz.ttpu.inventory.references.api.BuildingDto;
import uz.ttpu.inventory.references.api.CategoryDto;
import uz.ttpu.inventory.references.api.DepartmentDto;
import uz.ttpu.inventory.references.api.EmployeeDto;
import uz.ttpu.inventory.references.api.FloorDto;
import uz.ttpu.inventory.references.api.ReferenceSyncApi;
import uz.ttpu.inventory.references.api.RoomDto;

class PilotDataMigrationServiceTest {

    private ReferenceSyncApi referenceSyncApi;
    private AssetLogisticsApi assetLogisticsApi;
    private PilotDataMigrationService migrationService;

    private UUID buildingId;
    private UUID floorId;
    private UUID roomId;
    private UUID departmentId;
    private UUID employeeId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        referenceSyncApi = mock(ReferenceSyncApi.class);
        assetLogisticsApi = mock(AssetLogisticsApi.class);
        migrationService = new PilotDataMigrationService(referenceSyncApi, assetLogisticsApi);

        buildingId = UUID.randomUUID();
        floorId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        when(referenceSyncApi.findBuildingByCode("B-MAIN")).thenReturn(Optional.of(new BuildingDto(
                buildingId, "B-MAIN", "Main Academic Building", "Kichik Xalqa Yo'li 17", "Main", true, Instant.now(), Instant.now()
        )));
        when(referenceSyncApi.findFloorByBuildingAndNumber(buildingId, 2)).thenReturn(Optional.of(new FloorDto(
                floorId, buildingId, 2, "2nd Floor", true, Instant.now(), Instant.now()
        )));
        when(referenceSyncApi.findRoomByBuildingAndRoomNumber(buildingId, "204")).thenReturn(Optional.of(new RoomDto(
                roomId, floorId, departmentId, "204", "CAD Lab", 30, true, Instant.now(), Instant.now()
        )));
        when(referenceSyncApi.findDepartmentByCode("DEP-IT")).thenReturn(Optional.of(new DepartmentDto(
                departmentId, "DEP-IT", "IT Department", null, buildingId, true, Instant.now(), Instant.now()
        )));
        when(referenceSyncApi.findEmployeeByCodeOrFullName("EMP-001", "Sardor Karimov")).thenReturn(Optional.of(new EmployeeDto(
                employeeId, "EMP-001", "Sardor", "Karimov", "Rustamovich", "s.karimov@ttpu.uz",
                "+998901234567", "Lab Head", departmentId, roomId, "H-001", "1C-EMP-001", true, Instant.now(), Instant.now()
        )));
        when(referenceSyncApi.findCategoryByCode("CAT-IT")).thenReturn(Optional.of(new CategoryDto(
                categoryId, "CAT-IT", "IT Equipment", null, 5, Map.of(), true, Instant.now(), Instant.now()
        )));

        when(assetLogisticsApi.upsertPilotMigratedItem(any(PilotItemUpsertRequest.class))).thenAnswer(inv -> {
            PilotItemUpsertRequest req = inv.getArgument(0);
            return new ItemDto(
                    UUID.randomUUID(),
                    req.inventoryNumber(),
                    req.serialNumber(),
                    "PILOTQR234",
                    req.name(),
                    null,
                    req.categoryId(),
                    req.roomId() != null ? ItemState.FOYDALANISHDA : ItemState.OMBORDA,
                    req.purchasePrice(),
                    req.residualValue(),
                    req.commissionedAt(),
                    null,
                    null,
                    req.buildingId(),
                    req.floorId(),
                    req.roomId(),
                    req.departmentId(),
                    req.custodianEmployeeId(),
                    req.customAttributes(),
                    req.oneCRefId(),
                    Instant.now(),
                    Instant.now()
            );
        });
    }

    @Test
    void validatesPilotXlsxFlagsMissingRoomsAndEmployeesAndMapsValidRowsToAssetAndReferenceTables() throws Exception {
        byte[] workbookBytes = buildPilotTestWorkbook();
        String base64Xlsx = Base64.getEncoder().encodeToString(workbookBytes);

        // 1. Dry-run in strictMode=true: validates all 3 rows, flags MISSING_ROOM and MISSING_EMPLOYEE, imports 0 items
        PilotMigrationResultDto dryRunResult = migrationService.importPilotBuildingXlsx(new PilotMigrationImportRequest(
                base64Xlsx,
                "B-MAIN",
                true,
                true,
                false
        ));

        assertThat(dryRunResult.totalRowsRead()).isEqualTo(3);
        assertThat(dryRunResult.validRowsCount()).isEqualTo(1);
        assertThat(dryRunResult.importedItemsCount()).isZero();
        assertThat(dryRunResult.mismatches())
                .extracting(m -> m.mismatchType())
                .contains(
                        PilotDataMigrationService.MISMATCH_MISSING_ROOM,
                        PilotDataMigrationService.MISMATCH_MISSING_EMPLOYEE,
                        PilotDataMigrationService.MISMATCH_INVALID_ROW_DATA
                );
        verify(assetLogisticsApi, never()).upsertPilotMigratedItem(any());

        // 2. Live import in strictMode=true: imports only the clean row (Row 2) and skips mismatched rows
        PilotMigrationResultDto strictLiveResult = migrationService.importPilotBuildingXlsx(new PilotMigrationImportRequest(
                base64Xlsx,
                "B-MAIN",
                false,
                true,
                false
        ));

        assertThat(strictLiveResult.totalRowsRead()).isEqualTo(3);
        assertThat(strictLiveResult.importedItemsCount()).isEqualTo(1);
        assertThat(strictLiveResult.skippedRowsCount()).isEqualTo(2);
        assertThat(strictLiveResult.rowResults().get(0).imported()).isTrue();
        assertThat(strictLiveResult.rowResults().get(0).qrCode()).isEqualTo("PILOTQR234");
        verify(assetLogisticsApi, times(1)).upsertPilotMigratedItem(any());

        // 3. Sample template generator produces valid XLSX workbook
        byte[] sampleTemplate = migrationService.generateSamplePilot1CXlsxTemplate();
        assertThat(sampleTemplate).isNotEmpty();
        assertThat(sampleTemplate[0]).isEqualTo((byte) 'P');
        assertThat(sampleTemplate[1]).isEqualTo((byte) 'K');
    }

    private byte[] buildPilotTestWorkbook() throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("1C_Pilot");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Inventory Number");
            header.createCell(1).setCellValue("Asset Name");

            // Row 1 (Excel Row 2): Clean valid row
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("TTPU-PILOT-001");
            r1.createCell(1).setCellValue("Dell Precision 3660 Workstation");
            r1.createCell(2).setCellValue("SN-3660-001");
            r1.createCell(3).setCellValue("Precision 3660");
            r1.createCell(4).setCellValue("CAT-IT");
            r1.createCell(5).setCellValue("IT Equipment");
            r1.createCell(6).setCellValue("B-MAIN");
            r1.createCell(7).setCellValue("2");
            r1.createCell(8).setCellValue("204");
            r1.createCell(9).setCellValue("DEP-IT");
            r1.createCell(10).setCellValue("EMP-001");
            r1.createCell(11).setCellValue("Sardor Karimov");
            r1.createCell(12).setCellValue("24000000.00");
            r1.createCell(13).setCellValue("21000000.00");
            r1.createCell(14).setCellValue("1C-REF-001");

            // Row 2 (Excel Row 3): Missing Room (999-UNKNOWN) and Missing Employee (EMP-999)
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("TTPU-PILOT-002");
            r2.createCell(1).setCellValue("Epson Laser Projector");
            r2.createCell(2).setCellValue("SN-EPSON-002");
            r2.createCell(3).setCellValue("EB-L200F");
            r2.createCell(4).setCellValue("CAT-IT");
            r2.createCell(5).setCellValue("IT Equipment");
            r2.createCell(6).setCellValue("B-MAIN");
            r2.createCell(7).setCellValue("2");
            r2.createCell(8).setCellValue("999-UNKNOWN");
            r2.createCell(9).setCellValue("DEP-IT");
            r2.createCell(10).setCellValue("EMP-999");
            r2.createCell(11).setCellValue("Unknown Person");
            r2.createCell(12).setCellValue("15000000.00");
            r2.createCell(13).setCellValue("12000000.00");
            r2.createCell(14).setCellValue("1C-REF-002");

            // Row 3 (Excel Row 4): Invalid financial data (residualValue > purchasePrice)
            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue("TTPU-PILOT-003");
            r3.createCell(1).setCellValue("Broken Price Row");
            r3.createCell(4).setCellValue("CAT-IT");
            r3.createCell(12).setCellValue("5000000.00");
            r3.createCell(13).setCellValue("9000000.00");

            wb.write(out);
            return out.toByteArray();
        }
    }
}
