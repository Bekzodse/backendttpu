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

    when(repository.saveAndFlush(any(DepreciationRecord.class)))
        .thenAnswer(
            inv -> {
              DepreciationRecord rec = inv.getArgument(0);
              if (rec.getId() == null) {
                rec.setId(UUID.randomUUID());
              }
              return rec;
            });

    FinanceMapper financeMapper = Mappers.getMapper(FinanceMapper.class);
    DepreciationService depreciationService =
        new DepreciationService(repository, assetLogisticsApi, financeMapper);

    // 1. Exact 5-year straight-line forecast: (42,000,000 - 2,000,000) / 5 = 8,000,000 / year
    UUID itemId = UUID.randomUUID();
    DepreciationForecastDto forecast =
        depreciationService.calculateAndSaveForecast(
            new DepreciationCalculationRequest(
                itemId,
                "INV-2026-777",
                "High-Precision Laser Cutter",
                new BigDecimal("42000000.00"),
                new BigDecimal("2000000.00"),
                5,
                2));

    assertThat(forecast.depreciableBase()).isEqualByComparingTo("40000000.00");
    assertThat(forecast.annualDepreciation()).isEqualByComparingTo("8000000.00");
    assertThat(forecast.monthlyDepreciation()).isEqualByComparingTo("666666.67");
    assertThat(forecast.accumulatedDepreciation()).isEqualByComparingTo("16000000.00");
    assertThat(forecast.netBookValue()).isEqualByComparingTo("26000000.00");
    assertThat(forecast.yearlySchedule()).hasSize(5);
    assertThat(forecast.yearlySchedule().get(0).openingBookValue())
        .isEqualByComparingTo("42000000.00");
    assertThat(forecast.yearlySchedule().get(0).closingBookValue())
        .isEqualByComparingTo("34000000.00");
    assertThat(forecast.yearlySchedule().get(4).closingBookValue())
        .isEqualByComparingTo("2000000.00");

    // 2. Non-divisible 3-year straight-line forecast: (10,000,000 - 0) / 3 -> final year adjusts
    // rounding penny
    DepreciationForecastDto nonDivisible =
        depreciationService.calculateAndSaveForecast(
            new DepreciationCalculationRequest(
                UUID.randomUUID(),
                "INV-2026-778",
                "Lab Server",
                new BigDecimal("10000000.00"),
                BigDecimal.ZERO,
                3,
                3));
    assertThat(nonDivisible.annualDepreciation()).isEqualByComparingTo("3333333.33");
    assertThat(nonDivisible.yearlySchedule()).hasSize(3);
    assertThat(nonDivisible.yearlySchedule().get(2).closingBookValue())
        .isEqualByComparingTo("0.00");
    assertThat(nonDivisible.netBookValue()).isEqualByComparingTo("0.00");
  }

  @Test
  void sxssfInventoryReportGenerates10000RowsUnder30Seconds() {
    ReportExportLogRepository logRepository = mock(ReportExportLogRepository.class);
    AssetLogisticsApi assetLogisticsApi = mock(AssetLogisticsApi.class);
    ReportMapper reportMapper = Mappers.getMapper(ReportMapper.class);

    when(logRepository.saveAndFlush(any(ReportExportLog.class)))
        .thenAnswer(
            inv -> {
              ReportExportLog log = inv.getArgument(0);
              if (log.getId() == null) {
                log.setId(UUID.randomUUID());
              }
              return log;
            });

    SxssfInventoryReportService reportService =
        new SxssfInventoryReportService(logRepository, assetLogisticsApi, reportMapper);

    UUID buildingId = UUID.randomUUID();
    UUID roomId = UUID.randomUUID();
    UUID deptId = UUID.randomUUID();
    UUID custodianId = UUID.randomUUID();
    Instant commissionedAt = Instant.parse("2026-01-15T10:00:00Z");

    List<InventoryReportRowDto> tenThousandRows = new ArrayList<>(10_000);
    for (int i = 1; i <= 10_000; i++) {
      tenThousandRows.add(
          new InventoryReportRowDto(
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
              commissionedAt));
    }

    GeneratedXlsxReport generated =
        reportService.generateInventoryXlsxReport(tenThousandRows, "auditor@ttpu.uz");

    assertThat(generated.log().rowCount()).isEqualTo(10_000);
    // Must complete in well under 30,000 ms (30 seconds)
    assertThat(generated.log().generationTimeMs()).isLessThan(30_000L);
    assertThat(generated.xlsxBytes().length).isGreaterThan(100_000);
    // Verify valid ZIP/OOXML header 'P', 'K', 3, 4
    assertThat(generated.xlsxBytes()[0]).isEqualTo((byte) 'P');
    assertThat(generated.xlsxBytes()[1]).isEqualTo((byte) 'K');
  }

  @Test
  void officialPdfPrintFormsFt129AndFt913GenerateValidPdfsWithEmbeddedEImzoSignatureMetadata()
      throws Exception {
    uz.ttpu.inventory.integration.api.EImzoSignatureLookupApi eimzoLookup =
        mock(uz.ttpu.inventory.integration.api.EImzoSignatureLookupApi.class);
    UUID operationId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();

    when(eimzoLookup.listSignaturesForDocument(any(UUID.class))).thenReturn(List.of());
    when(eimzoLookup.verifyAndRecordSignedPdfBlob(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenAnswer(
            inv ->
                new uz.ttpu.inventory.integration.api.EImzoSignatureRecordDto(
                    UUID.randomUUID(),
                    inv.getArgument(0),
                    inv.getArgument(1),
                    inv.getArgument(2),
                    "a1b2c3d4e5f6",
                    ((byte[]) inv.getArgument(3)).length,
                    "EIMZO-PKCS7-TEST-999",
                    inv.getArgument(4),
                    inv.getArgument(5),
                    inv.getArgument(6),
                    inv.getArgument(7),
                    inv.getArgument(8),
                    true,
                    "Verified by E-IMZO PKCS#7 Authority",
                    Instant.now(),
                    Instant.now(),
                    Instant.now()));

    uz.ttpu.inventory.report.service.PdfPrintFormService pdfService =
        new uz.ttpu.inventory.report.service.PdfPrintFormService(eimzoLookup);

    // 1. Verify editable templates list (FT-12.9 & FT-9.13)
    var templates = pdfService.listTemplates();
    assertThat(templates).hasSize(4);

    // 2. Generate FT-12.9 Handover Act (Dalolatnoma) PDF
    var handoverDoc =
        pdfService.generateHandoverActPdf(
            new uz.ttpu.inventory.report.api.HandoverActPrintRequest(
                operationId,
                "ACT-FT129-001",
                "ISSUE",
                "ISSUE_ACCEPTED",
                UUID.randomUUID(),
                "TTPU-INV-00010",
                "JBSWY3DPEB",
                "Dell Precision Workstation",
                "SN-DELL-10",
                "FOYDALANISHDA",
                new BigDecimal("28500000.00"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                true,
                "Issued to CAD Lab",
                Instant.now()));

    assertThat(handoverDoc.formCode()).isEqualTo("FT-12.9");
    assertThat(new String(handoverDoc.pdfBytes(), 0, 5)).isEqualTo("%PDF-");

    // Attach E-IMZO signature metadata to the FT-12.9 Handover Act PDF
    var signedHandoverDoc =
        pdfService.attachEImzoSignatureToPdf(
            handoverDoc,
            "30101800010014",
            "201122919",
            "Prof. Jamshid Inoyatkhodjaev",
            "TTPU",
            "CERT-TTPU-2026-01");
    assertThat(signedHandoverDoc.eimzoSignatures()).hasSize(1);
    try (org.apache.pdfbox.pdmodel.PDDocument pdDoc =
        org.apache.pdfbox.pdmodel.PDDocument.load(signedHandoverDoc.pdfBytes())) {
      var info = pdDoc.getDocumentInformation();
      assertThat(info.getCustomMetadataValue("TTPU-Form-Code")).isEqualTo("FT-12.9");
      assertThat(info.getCustomMetadataValue("EIMZO-Signed")).isEqualTo("true");
      assertThat(info.getCustomMetadataValue("EIMZO-Signature-Count")).isEqualTo("1");
      assertThat(info.getCustomMetadataValue("EIMZO-PKCS7-Ids")).isEqualTo("EIMZO-PKCS7-TEST-999");
      assertThat(info.getCustomMetadataValue("EIMZO-Signer-PINFLs")).isEqualTo("30101800010014");
    }

    // 3. Generate FT-9.13 Opis, Comparison Statement, and Final Act PDFs
    var sessionPrintReq =
        new uz.ttpu.inventory.report.api.InventorySessionPrintRequest(
            sessionId,
            "INV-2026-Q4",
            "ORD-2026-77",
            Instant.parse("2026-11-01T00:00:00Z"),
            Instant.parse("2026-11-07T00:00:00Z"),
            "COMPLETED",
            UUID.randomUUID(),
            List.of(UUID.randomUUID(), UUID.randomUUID()),
            List.of(UUID.randomUUID()),
            1,
            1,
            1,
            1,
            List.of(
                new uz.ttpu.inventory.report.api.InventorySessionPrintRequest.ExpectedItemPrintRow(
                    UUID.randomUUID(),
                    "TTPU-INV-00010",
                    "JBSWY3DPEB",
                    "Dell Precision Workstation",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new BigDecimal("28500000.00"),
                    "FOUND",
                    Instant.now())),
            List.of(
                new uz.ttpu.inventory.report.api.InventorySessionPrintRequest
                    .ReconciledItemPrintRow(
                    UUID.randomUUID(),
                    "TTPU-INV-00010",
                    "JBSWY3DPEB",
                    "Dell Precision Workstation",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "JOYIDA",
                    "Joyida (In Place)",
                    false,
                    Instant.now())));

    var opisPdf = pdfService.generateInventoryOpisPdf(sessionPrintReq);
    var comparisonPdf = pdfService.generateInventoryComparisonStatementPdf(sessionPrintReq);
    var finalActPdf = pdfService.generateInventoryFinalActPdf(sessionPrintReq);

    assertThat(new String(opisPdf.pdfBytes(), 0, 5)).isEqualTo("%PDF-");
    assertThat(new String(comparisonPdf.pdfBytes(), 0, 5)).isEqualTo("%PDF-");
    assertThat(new String(finalActPdf.pdfBytes(), 0, 5)).isEqualTo("%PDF-");
    assertThat(opisPdf.formCode()).isEqualTo("FT-9.13");
    assertThat(comparisonPdf.formCode()).isEqualTo("FT-9.13");
    assertThat(finalActPdf.formCode()).isEqualTo("FT-9.13");
  }
}
