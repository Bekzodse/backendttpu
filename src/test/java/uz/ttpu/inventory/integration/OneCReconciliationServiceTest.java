package uz.ttpu.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.integration.api.OneCAssetRecordDto;
import uz.ttpu.inventory.integration.api.OneCReconciliationMismatchDto;
import uz.ttpu.inventory.integration.api.OneCReconciliationReportDto;
import uz.ttpu.inventory.integration.service.IntegrationResilienceConfig;
import uz.ttpu.inventory.integration.service.OneCExternalClientStub;
import uz.ttpu.inventory.integration.service.OneCReconciliationService;
import uz.ttpu.inventory.references.api.EmployeeDto;
import uz.ttpu.inventory.references.api.ReferenceSyncApi;

class OneCReconciliationServiceTest {

  private AssetLogisticsApi assetLogisticsApi;
  private ReferenceSyncApi referenceSyncApi;
  private OneCExternalClientStub oneCClientStub;
  private OneCReconciliationService reconciliationService;

  private UUID empId1;
  private UUID empId2;

  @BeforeEach
  void setUp() {
    assetLogisticsApi = mock(AssetLogisticsApi.class);
    referenceSyncApi = mock(ReferenceSyncApi.class);
    oneCClientStub = new OneCExternalClientStub();

    IntegrationResilienceConfig config = new IntegrationResilienceConfig();
    RetryRegistry retryRegistry = config.integrationRetryRegistry(3, 10L);
    CircuitBreakerRegistry cbRegistry = config.integrationCircuitBreakerRegistry(50.0f, 4, 6, 30L);

    reconciliationService =
        new OneCReconciliationService(
            assetLogisticsApi, referenceSyncApi, oneCClientStub, cbRegistry, retryRegistry);

    empId1 = UUID.randomUUID();
    empId2 = UUID.randomUUID();

    when(referenceSyncApi.findEmployeeById(empId1))
        .thenReturn(
            Optional.of(
                new EmployeeDto(
                    empId1,
                    "EMP-001",
                    "Azizbek",
                    "Tursunov",
                    null,
                    "aziz@ttpu.uz",
                    "+998901234501",
                    "Associate Professor",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "HEMIS-1",
                    "1C-1",
                    true,
                    Instant.now(),
                    Instant.now())));

    when(referenceSyncApi.findEmployeeById(empId2))
        .thenReturn(
            Optional.of(
                new EmployeeDto(
                    empId2,
                    "EMP-002",
                    "Malika",
                    "Yusupova",
                    null,
                    "malika@ttpu.uz",
                    "+998901234502",
                    "Engineer",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "HEMIS-2",
                    "1C-2",
                    true,
                    Instant.now(),
                    Instant.now())));
  }

  @Test
  @DisplayName("Full reconciliation produces CLEAN report when local items match 1C records")
  void reconcileWith1C_clean_whenAllItemsMatch() {
    ItemDto item1 =
        createSampleItem(
            UUID.randomUUID(),
            "INV-2026-0001",
            "Dell OptiPlex 7090 Desktop",
            new BigDecimal("12500000.00"),
            empId1);
    ItemDto item2 =
        createSampleItem(
            UUID.randomUUID(),
            "INV-2026-0002",
            "HP LaserJet Pro M404dn",
            new BigDecimal("4200000.00"),
            empId2);

    when(assetLogisticsApi.listAllItemsForReporting()).thenReturn(List.of(item1, item2));

    oneCClientStub.setCustomRecords(
        List.of(
            new OneCAssetRecordDto(
                "INV-2026-0001",
                "Dell OptiPlex 7090 Desktop",
                new BigDecimal("12500000.00"),
                "EMP-001 - Azizbek Tursunov",
                "1C-001"),
            new OneCAssetRecordDto(
                "INV-2026-0002",
                "HP LaserJet Pro M404dn",
                new BigDecimal("4200000.00"),
                "EMP-002 - Malika Yusupova",
                "1C-002")));

    OneCReconciliationReportDto report = reconciliationService.reconcileWith1C();

    assertThat(report.status()).isEqualTo("CLEAN");
    assertThat(report.totalLocalItems()).isEqualTo(2);
    assertThat(report.totalOneCItems()).isEqualTo(2);
    assertThat(report.matchedCount()).isEqualTo(2);
    assertThat(report.mismatchCount()).isEqualTo(0);
    assertThat(report.mismatches()).isEmpty();
  }

  @Test
  @DisplayName("Full reconciliation flags financial value and responsible person mismatches")
  void reconcileWith1C_flagsDiscrepancies() {
    ItemDto item1 =
        createSampleItem(
            UUID.randomUUID(),
            "INV-2026-0001",
            "Dell OptiPlex 7090 Desktop",
            new BigDecimal("10000000.00"), // Local is 10M
            empId1);
    ItemDto item2 =
        createSampleItem(
            UUID.randomUUID(),
            "INV-2026-0002",
            "HP LaserJet Pro M404dn",
            new BigDecimal("4200000.00"),
            empId1); // Local has empId1, but 1C has empId2

    when(assetLogisticsApi.listAllItemsForReporting()).thenReturn(List.of(item1, item2));

    oneCClientStub.setCustomRecords(
        List.of(
            new OneCAssetRecordDto(
                "INV-2026-0001",
                "Dell OptiPlex 7090 Desktop",
                new BigDecimal("12500000.00"), // 1C is 12.5M -> mismatch!
                "EMP-001 - Azizbek Tursunov",
                "1C-001"),
            new OneCAssetRecordDto(
                "INV-2026-0002",
                "HP LaserJet Pro M404dn",
                new BigDecimal("4200000.00"),
                "EMP-999 - Other Custodian", // Different person -> mismatch!
                "1C-002")));

    OneCReconciliationReportDto report = reconciliationService.reconcileWith1C();

    assertThat(report.status()).isEqualTo("DISCREPANCIES_FOUND");
    assertThat(report.mismatchCount()).isEqualTo(2);

    List<String> types =
        report.mismatches().stream().map(OneCReconciliationMismatchDto::mismatchType).toList();
    assertThat(types)
        .contains(
            OneCReconciliationService.MISMATCH_FINANCIAL_VALUE,
            OneCReconciliationService.MISMATCH_RESPONSIBLE_PERSON);
  }

  @Test
  @DisplayName("Full reconciliation flags missing items in both 1C and local systems")
  void reconcileWith1C_flagsMissingItems() {
    ItemDto localOnly =
        createSampleItem(
            UUID.randomUUID(),
            "INV-LOCAL-999",
            "Custom Lab Rig",
            new BigDecimal("5000000.00"),
            empId1);

    when(assetLogisticsApi.listAllItemsForReporting()).thenReturn(List.of(localOnly));

    oneCClientStub.setCustomRecords(
        List.of(
            new OneCAssetRecordDto(
                "INV-1C-888",
                "Mainframe Server",
                new BigDecimal("99000000.00"),
                "EMP-002",
                "1C-888")));

    OneCReconciliationReportDto report = reconciliationService.reconcileWith1C();

    assertThat(report.status()).isEqualTo("DISCREPANCIES_FOUND");
    assertThat(report.mismatchCount()).isEqualTo(2);

    List<String> types =
        report.mismatches().stream().map(OneCReconciliationMismatchDto::mismatchType).toList();
    assertThat(types)
        .contains(
            OneCReconciliationService.MISMATCH_NOT_FOUND_IN_1C,
            OneCReconciliationService.MISMATCH_NOT_FOUND_IN_LOCAL);
  }

  @Test
  @DisplayName("Nightly scheduled job runs and persists latest reconciliation report in memory")
  void runNightlyReconciliationJob_persistsReport() {
    when(assetLogisticsApi.listAllItemsForReporting()).thenReturn(List.of());
    oneCClientStub.setCustomRecords(List.of());

    reconciliationService.runNightlyReconciliationJob();

    assertThat(reconciliationService.getLatestReport()).isPresent();
    assertThat(reconciliationService.getLatestReport().get().status()).isEqualTo("CLEAN");
  }

  private ItemDto createSampleItem(
      UUID id, String invNum, String name, BigDecimal price, UUID custodianId) {
    return new ItemDto(
        id,
        invNum,
        "SN-" + invNum,
        "QR-" + invNum,
        name,
        "Description",
        UUID.randomUUID(),
        ItemState.FOYDALANISHDA,
        price,
        price,
        false,
        Instant.now(),
        null,
        null,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        custodianId,
        null,
        "1C-REF-" + invNum,
        Instant.now(),
        Instant.now());
  }
}
