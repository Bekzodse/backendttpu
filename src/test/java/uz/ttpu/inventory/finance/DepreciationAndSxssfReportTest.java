package uz.ttpu.inventory.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.finance.api.DepreciationCalculationRequest;
import uz.ttpu.inventory.finance.api.DepreciationForecastDto;
import uz.ttpu.inventory.finance.domain.DepreciationRecord;
import uz.ttpu.inventory.finance.repository.DepreciationRecordRepository;
import uz.ttpu.inventory.finance.service.DepreciationService;
import uz.ttpu.inventory.finance.service.FinanceMapper;
import uz.ttpu.inventory.report.api.InventoryReportRowDto;
import uz.ttpu.inventory.report.domain.ReportExportLog;
import uz.ttpu.inventory.report.repository.ReportExportLogRepository;
import uz.ttpu.inventory.report.service.ReportMapper;
import uz.ttpu.inventory.report.service.SxssfInventoryReportService;
import uz.ttpu.inventory.report.service.SxssfInventoryReportService.GeneratedXlsxReport;

class DepreciationAndSxssfReportTest {

    @Test
    void straightLineDepreciationCalculatesFormulaAndFullYearlySchedule() {
        DepreciationRecordRepository repository = mock(DepreciationRecordRepository.class);
        AssetLogisticsApi assetLogisticsApi = mock(AssetLogisticsApi.class);

        when(repository.saveAndFlush(any(DepreciationRecord.class))).thenAnswer(inv -> {
            DepreciationRecord rec = inv.getArgument(0);
            if (rec.getId() == null) {
                rec.setId(UUID.randomUUID());
            }
            return rec;
        });

        FinanceMapper financeMapper = Mappers.getMapper(FinanceMapper.class);
        DepreciationService depreciationService = new DepreciationService(repository, assetLogisticsApi, financeMapper);

        // 1. Exact 5-year straight-line forecast: (42,000,000 - 2,000,000) / 5 = 8,000,000 / year
        UUID itemId = UUID.randomUUID();
        DepreciationForecastDto forecast = depreciationService.calculateAndSaveForecast(new DepreciationCalculationRequest(
                itemId,
                "INV-2026-777",
                "High-Precision Laser Cutter",
                new BigDecimal("42000000.00"),
                new BigDecimal("2000000.00"),
                5,
                2
        ));

        assertThat(forecast.depreciableBase()).isEqualByComparingTo("40000000.00");
        assertThat(forecast.annualDepreciation()).isEqualByComparingTo("8000000.00");
        assertThat(forecast.monthlyDepreciation()).isEqualByComparingTo("666666.67");
        assertThat(forecast.accumulatedDepreciation()).isEqualByComparingTo("16000000.00");
        assertThat(forecast.netBookValue()).isEqualByComparingTo("26000000.00");
        assertThat(forecast.yearlySchedule()).hasSize(5);
        assertThat(forecast.yearlySchedule().get(0).openingBookValue()).isEqualByComparingTo("42000000.00");
        assertThat(forecast.yearlySchedule().get(0).closingBookValue()).isEqualByComparingTo("34000000.00");
        assertThat(forecast.yearlySchedule().get(4).closingBookValue()).isEqualByComparingTo("2000000.00");

        // 2. Non-divisible 3-year straight-line forecast: (10,000,000 - 0) / 3 -> final year adjusts rounding penny
        DepreciationForecastDto nonDivisible = depreciationService.calculateAndSaveForecast(new DepreciationCalculationRequest(
                UUID.randomUUID(),
                "INV-2026-778",
                "Lab Server",
                new BigDecimal("10000000.00"),
                BigDecimal.ZERO,
                3,
                3
        ));
        assertThat(nonDivisible.annualDepreciation()).isEqualByComparingTo("3333333.33");
        assertThat(nonDivisible.yearlySchedule()).hasSize(3);
        assertThat(nonDivisible.yearlySchedule().get(2).closingBookValue()).isEqualByComparingTo("0.00");
        assertThat(nonDivisible.netBookValue()).isEqualByComparingTo("0.00");
    }

    @Test
    void sxssfInventoryReportGenerates10000RowsUnder30Seconds() {
        ReportExportLogRepository logRepository = mock(ReportExportLogRepository.class);
        AssetLogisticsApi assetLogisticsApi = mock(AssetLogisticsApi.class);
        ReportMapper reportMapper = Mappers.getMapper(ReportMapper.class);

        when(logRepository.saveAndFlush(any(ReportExportLog.class))).thenAnswer(inv -> {
            ReportExportLog log = inv.getArgument(0);
            if (log.getId() == null) {
                log.setId(UUID.randomUUID());
            }
            return log;
        });

        SxssfInventoryReportService reportService = new SxssfInventoryReportService(
                logRepository,
                assetLogisticsApi,
                reportMapper
        );

        UUID buildingId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID custodianId = UUID.randomUUID();
        Instant commissionedAt = Instant.parse("2026-01-15T10:00:00Z");

        List<InventoryReportRowDto> tenThousandRows = new ArrayList<>(10_000);
        for (int i = 1; i <= 10_000; i++) {
            tenThousandRows.add(new InventoryReportRowDto(
                    String.format("TTPU-INV-%05d", i),
                    String.format("QR%08d", i),
                    "University Workstation #" + i,
                    String.format("SN-WS-%05d", i),
                    "FOYDALANISHDA",
                    new BigDecimal("15000000.00"),
                    new BigDecimal("12000000.00"),
                    buildingId,
                    roomId,
                    deptId,
                    custodianId,
                    commissionedAt
            ));
        }

        GeneratedXlsxReport generated = reportService.generateInventoryXlsxReport(tenThousandRows, "auditor@ttpu.uz");

        assertThat(generated.log().rowCount()).isEqualTo(10_000);
        // Must complete in well under 30,000 ms (30 seconds)
        assertThat(generated.log().generationTimeMs()).isLessThan(30_000L);
        assertThat(generated.xlsxBytes().length).isGreaterThan(100_000);
        // Verify valid ZIP/OOXML header 'P', 'K', 3, 4
        assertThat(generated.xlsxBytes()[0]).isEqualTo((byte) 'P');
        assertThat(generated.xlsxBytes()[1]).isEqualTo((byte) 'K');
    }
}
