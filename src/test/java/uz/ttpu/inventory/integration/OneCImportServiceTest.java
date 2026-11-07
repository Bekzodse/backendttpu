package uz.ttpu.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.asset.api.PilotItemUpsertRequest;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.integration.api.OneCImportCommitResultDto;
import uz.ttpu.inventory.integration.api.OneCImportConfirmRequest;
import uz.ttpu.inventory.integration.api.OneCImportPreviewRequest;
import uz.ttpu.inventory.integration.api.OneCImportPreviewResponseDto;
import uz.ttpu.inventory.integration.service.OneCImportService;
import uz.ttpu.inventory.references.api.BuildingDto;
import uz.ttpu.inventory.references.api.CategoryDto;
import uz.ttpu.inventory.references.api.DepartmentDto;
import uz.ttpu.inventory.references.api.EmployeeDto;
import uz.ttpu.inventory.references.api.FloorDto;
import uz.ttpu.inventory.references.api.ReferenceSyncApi;
import uz.ttpu.inventory.references.api.RoomDto;

class OneCImportServiceTest {

  private ReferenceSyncApi referenceSyncApi;
  private AssetLogisticsApi assetLogisticsApi;
  private OneCImportService service;

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
    service = new OneCImportService(referenceSyncApi, assetLogisticsApi);

    buildingId = UUID.randomUUID();
    floorId = UUID.randomUUID();
    roomId = UUID.randomUUID();
    departmentId = UUID.randomUUID();
    employeeId = UUID.randomUUID();
    categoryId = UUID.randomUUID();

    when(referenceSyncApi.findBuildingByCode("B-MAIN"))
        .thenReturn(
            Optional.of(
                new BuildingDto(
                    buildingId,
                    "B-MAIN",
                    "Main Academic Building",
                    "Universitet 17",
                    "Main",
                    true,
                    Instant.now(),
                    Instant.now())));
    when(referenceSyncApi.findFloorByBuildingAndNumber(buildingId, 2))
        .thenReturn(
            Optional.of(
                new FloorDto(
                    floorId, buildingId, 2, "2nd Floor", true, Instant.now(), Instant.now())));
    when(referenceSyncApi.findRoomByBuildingAndRoomNumber(buildingId, "204"))
        .thenReturn(
            Optional.of(
                new RoomDto(
                    roomId,
                    floorId,
                    departmentId,
                    "204",
                    "CAD Lab",
                    30,
                    true,
                    Instant.now(),
                    Instant.now())));
    when(referenceSyncApi.findCategoryByCode("CAT-IT"))
        .thenReturn(
            Optional.of(
                new CategoryDto(
                    categoryId,
                    "CAT-IT",
                    "IT & Computing",
                    null,
                    5,
                    Map.of(),
                    true,
                    Instant.now(),
                    Instant.now())));
    when(referenceSyncApi.findEmployeeByCodeOrFullName("EMP-001", "Sardor Karimov"))
        .thenReturn(
            Optional.of(
                new EmployeeDto(
                    employeeId,
                    "EMP-001",
                    "Sardor",
                    "Karimov",
                    "Rustamovich",
                    "s.karimov@ttpu.uz",
                    "+998901234567",
                    "Lab Head",
                    departmentId,
                    roomId,
                    "H-001",
                    "1C-EMP-001",
                    true,
                    Instant.now(),
                    Instant.now())));
    when(referenceSyncApi.findEmployeeByCodeOrFullName("EMP-001", null))
        .thenReturn(
            Optional.of(
                new EmployeeDto(
                    employeeId,
                    "EMP-001",
                    "Sardor",
                    "Karimov",
                    "Rustamovich",
                    "s.karimov@ttpu.uz",
                    "+998901234567",
                    "Lab Head",
                    departmentId,
                    roomId,
                    "H-001",
                    "1C-EMP-001",
                    true,
                    Instant.now(),
                    Instant.now())));
    when(referenceSyncApi.findDepartmentByCode("DEP-IT"))
        .thenReturn(
            Optional.of(
                new DepartmentDto(
                    departmentId,
                    "DEP-IT",
                    "Information Technologies",
                    null,
                    buildingId,
                    true,
                    Instant.now(),
                    Instant.now())));

    when(assetLogisticsApi.existsByInventoryNumber(any())).thenReturn(false);
    when(assetLogisticsApi.existsBySerialNumber(any())).thenReturn(false);

    when(assetLogisticsApi.upsertPilotMigratedItem(any(PilotItemUpsertRequest.class)))
        .thenAnswer(
            inv -> {
              PilotItemUpsertRequest req = inv.getArgument(0);
              return new ItemDto(
                  UUID.randomUUID(),
                  req.inventoryNumber(),
                  req.serialNumber(),
                  "QR-" + req.inventoryNumber(),
                  req.name(),
                  null,
                  req.categoryId(),
                  ItemState.FOYDALANISHDA,
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
                  Instant.now());
            });
  }

  @Test
  @DisplayName("Creates error preview for CSV without committing to database")
  void createsPreviewWithoutCommitting() {
    String csvContent =
        """
Inventory Number,Asset Name,Serial Number,Model,Category Code,Category Name,Building Code,Floor Number,Room Number,Department Code,Employee Code,Employee Full Name,Purchase Price,Residual Value,1C Ref ID
TTPU-001,MacBook Pro 16,SN-MBP-01,M2 Pro,CAT-IT,IT,B-MAIN,2,204,DEP-IT,EMP-001,Sardor Karimov,25000000.00,20000000.00,1C-01
TTPU-002,Dell Monitor 27,SN-DELL-01,U2723QE,CAT-IT,IT,B-MAIN,2,204,DEP-IT,EMP-001,Sardor Karimov,5000000.00,4000000.00,1C-02
""";

    String base64 = Base64.getEncoder().encodeToString(csvContent.getBytes(StandardCharsets.UTF_8));
    OneCImportPreviewResponseDto preview =
        service.createPreview(
            new OneCImportPreviewRequest(base64, "test-import.csv", "B-MAIN", false));

    assertThat(preview.stagingId()).isNotNull();
    assertThat(preview.fileType()).isEqualTo("CSV");
    assertThat(preview.totalRows()).isEqualTo(2);
    assertThat(preview.validRowsCount()).isEqualTo(2);
    assertThat(preview.errorRowsCount()).isEqualTo(0);
    assertThat(preview.canCommit()).isTrue();

    // Ensure database was NOT touched during preview phase!
    verify(assetLogisticsApi, never()).upsertPilotMigratedItem(any());
  }

  @Test
  @DisplayName("Flags in-file duplicates and missing room/employee references")
  void flagsDuplicatesAndMissingReferences() {
    // Row 1: valid
    // Row 2: duplicate inventory number & missing room 999
    // Row 3: duplicate serial number & missing employee EMP-999
    String csvContent =
        """
Inventory Number,Asset Name,Serial Number,Model,Category Code,Category Name,Building Code,Floor Number,Room Number,Department Code,Employee Code,Employee Full Name,Purchase Price,Residual Value,1C Ref ID
TTPU-001,MacBook Pro 16,SN-MBP-01,M2 Pro,CAT-IT,IT,B-MAIN,2,204,DEP-IT,EMP-001,Sardor Karimov,25000000.00,20000000.00,1C-01
TTPU-001,Dell Monitor 27,SN-DELL-01,U2723QE,CAT-IT,IT,B-MAIN,2,999,DEP-IT,EMP-001,Sardor Karimov,5000000.00,4000000.00,1C-02
TTPU-003,Dell Workstation,SN-MBP-01,T3660,CAT-IT,IT,B-MAIN,2,204,DEP-IT,EMP-999,Unknown Employee,15000000.00,12000000.00,1C-03
""";

    String base64 = Base64.getEncoder().encodeToString(csvContent.getBytes(StandardCharsets.UTF_8));
    OneCImportPreviewResponseDto preview =
        service.createPreview(
            new OneCImportPreviewRequest(base64, "mismatches.csv", "B-MAIN", false));

    assertThat(preview.totalRows()).isEqualTo(3);
    assertThat(preview.validRowsCount()).isEqualTo(1);
    assertThat(preview.errorRowsCount()).isEqualTo(2);

    assertThat(preview.duplicateInventoryNumbers()).hasSize(1);
    assertThat(preview.duplicateSerialNumbers()).hasSize(1);
    assertThat(preview.missingRooms()).hasSize(1);
    assertThat(preview.missingEmployees()).hasSize(1);

    verify(assetLogisticsApi, never()).upsertPilotMigratedItem(any());
  }

  @Test
  @DisplayName("Commits valid rows in a single transaction upon user confirmation")
  void commitsValidRowsInSingleTransaction() {
    String csvContent =
        """
Inventory Number,Asset Name,Serial Number,Model,Category Code,Category Name,Building Code,Floor Number,Room Number,Department Code,Employee Code,Employee Full Name,Purchase Price,Residual Value,1C Ref ID
TTPU-101,Cisco Switch 48P,SN-CS-01,C9300,CAT-IT,IT,B-MAIN,2,204,DEP-IT,EMP-001,Sardor Karimov,30000000.00,25000000.00,1C-101
TTPU-102,HP LaserJet Pro,SN-HP-01,M404,CAT-IT,IT,B-MAIN,2,204,DEP-IT,EMP-001,Sardor Karimov,4000000.00,3500000.00,1C-102
""";

    String base64 = Base64.getEncoder().encodeToString(csvContent.getBytes(StandardCharsets.UTF_8));
    OneCImportPreviewResponseDto preview =
        service.createPreview(new OneCImportPreviewRequest(base64, "valid.csv", "B-MAIN", false));

    // User confirms commit
    OneCImportCommitResultDto commitResult =
        service.confirmAndCommitImport(
            preview.stagingId(), new OneCImportConfirmRequest(true, false));

    assertThat(commitResult.status()).isEqualTo("COMMITTED");
    assertThat(commitResult.importedCount()).isEqualTo(2);
    assertThat(commitResult.skippedCount()).isEqualTo(0);
    assertThat(commitResult.importedInventoryNumbers()).containsExactly("TTPU-101", "TTPU-102");

    // Verify items were persisted
    verify(assetLogisticsApi, times(2)).upsertPilotMigratedItem(any(PilotItemUpsertRequest.class));

    // Cannot commit again (idempotency / single commit protection)
    assertThatThrownBy(
            () ->
                service.confirmAndCommitImport(
                    preview.stagingId(), new OneCImportConfirmRequest(true, false)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already been committed");
  }
}
