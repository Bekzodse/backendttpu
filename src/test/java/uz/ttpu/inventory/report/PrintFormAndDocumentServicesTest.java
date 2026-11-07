package uz.ttpu.inventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.integration.api.EImzoSignatureLookupApi;
import uz.ttpu.inventory.inventory.service.InventoryDocumentPrintService;
import uz.ttpu.inventory.movement.domain.MovementEventType;
import uz.ttpu.inventory.movement.domain.MovementRecord;
import uz.ttpu.inventory.movement.domain.MovementType;
import uz.ttpu.inventory.movement.repository.MovementRepository;
import uz.ttpu.inventory.movement.service.MovementHandoverActService;
import uz.ttpu.inventory.report.api.PrintTemplateDto;
import uz.ttpu.inventory.report.service.PdfPrintFormService;

class PrintFormAndDocumentServicesTest {

  @Test
  void editablePrintTemplateOverrideValidatesXhtmlAndRendersCustomPdf() {
    EImzoSignatureLookupApi eimzoLookup = mock(EImzoSignatureLookupApi.class);
    when(eimzoLookup.listSignaturesForDocument(any())).thenReturn(List.of());
    PdfPrintFormService pdfService = new PdfPrintFormService(eimzoLookup);

    // Unknown template key throws ResourceNotFoundException
    assertThatThrownBy(
            () ->
                pdfService.updateTemplate(
                    "unknown-template",
                    "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body>ok</body></html>"))
        .isInstanceOf(ResourceNotFoundException.class);

    // Malformed XHTML is rejected with IllegalArgumentException
    assertThatThrownBy(
            () ->
                pdfService.updateTemplate(
                    PdfPrintFormService.TEMPLATE_HANDOVER_ACT,
                    "<html><body><div>Unclosed div</body></html>"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid XHTML");

    // Valid custom XHTML override succeeds and marks customized=true
    String customXhtml =
        """
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:th="http://www.thymeleaf.org">
<head><title>Custom FT-12.9</title></head>
<body><h1>Custom TTPU FT-12.9 Act</h1><p th:text="${req.documentNumber}">DOC</p></body>
</html>
""";
    PrintTemplateDto updated =
        pdfService.updateTemplate(PdfPrintFormService.TEMPLATE_HANDOVER_ACT, customXhtml);
    assertThat(updated.customized()).isTrue();
    assertThat(updated.formCode()).isEqualTo("FT-12.9");
  }

  @Test
  void movementHandoverActAndInventoryDocumentPrintServicesGeneratePdfsAndEnforceValidation() {
    EImzoSignatureLookupApi eimzoLookup = mock(EImzoSignatureLookupApi.class);
    when(eimzoLookup.listSignaturesForDocument(any())).thenReturn(List.of());
    PdfPrintFormService pdfPrintFormService = new PdfPrintFormService(eimzoLookup);

    // 1. MovementHandoverActService
    MovementRepository movementRepository = mock(MovementRepository.class);
    AssetLogisticsApi assetLogisticsApi = mock(AssetLogisticsApi.class);
    MovementHandoverActService handoverActService =
        new MovementHandoverActService(movementRepository, assetLogisticsApi, pdfPrintFormService);

    UUID missingOpId = UUID.randomUUID();
    when(movementRepository.findAllByOperationIdOrderByRecordedAtAsc(missingOpId))
        .thenReturn(List.of());
    assertThatThrownBy(() -> handoverActService.generateHandoverActPdf(missingOpId))
        .isInstanceOf(ResourceNotFoundException.class);

    UUID opId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    MovementRecord record =
        MovementRecord.builder()
            .operationId(opId)
            .documentNumber("ACT-MOV-2026-01")
            .movementType(MovementType.ISSUE)
            .eventType(MovementEventType.ISSUE_ACCEPTED)
            .itemId(itemId)
            .toRoomId(UUID.randomUUID())
            .toEmployeeId(UUID.randomUUID())
            .totpVerified(true)
            .recordedAt(Instant.now())
            .build();
    when(movementRepository.findAllByOperationIdOrderByRecordedAtAsc(opId))
        .thenReturn(List.of(record));
    when(assetLogisticsApi.resolveItemByIdOrQr(itemId, null))
        .thenReturn(
            new ItemDto(
                itemId,
                "TTPU-INV-900",
                "SN-900",
                "QR900ABCDE",
                "Cisco Router",
                "Core router",
                UUID.randomUUID(),
                ItemState.FOYDALANISHDA,
                new BigDecimal("18000000.00"),
                new BigDecimal("16000000.00"),
                Instant.now(),
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Map.of(),
                "1C-900",
                Instant.now(),
                Instant.now()));

    var movPdf = handoverActService.generateHandoverActPdf(opId);
    assertThat(movPdf.formCode()).isEqualTo("FT-12.9");
    assertThat(new String(movPdf.pdfBytes(), 0, 5)).isEqualTo("%PDF-");

    // 2. InventoryDocumentPrintService
    uz.ttpu.inventory.inventory.repository.InventorySessionRepository sessionRepository =
        mock(uz.ttpu.inventory.inventory.repository.InventorySessionRepository.class);
    uz.ttpu.inventory.inventory.repository.InventoryExpectedItemRepository expectedItemRepository =
        mock(uz.ttpu.inventory.inventory.repository.InventoryExpectedItemRepository.class);
    uz.ttpu.inventory.inventory.repository.InventoryScanActionRepository scanActionRepository =
        mock(uz.ttpu.inventory.inventory.repository.InventoryScanActionRepository.class);

    UUID sessionId = UUID.randomUUID();
    uz.ttpu.inventory.inventory.domain.InventorySession session =
        uz.ttpu.inventory.inventory.domain.InventorySession.builder()
            .sessionCode("INV-SES-99")
            .rectorOrderNumber("ORD-99")
            .rectorOrderDate(Instant.now())
            .sessionDate(Instant.now())
            .status(uz.ttpu.inventory.inventory.domain.InventorySessionStatus.COMPLETED)
            .build();
    session.setId(sessionId);

    when(sessionRepository.findById(sessionId)).thenReturn(java.util.Optional.of(session));
    when(expectedItemRepository.findAllBySessionIdOrderByInventoryNumberAsc(sessionId))
        .thenReturn(List.of());
    when(scanActionRepository.findAllBySessionIdOrderByScannedAtAsc(sessionId))
        .thenReturn(List.of());

    InventoryDocumentPrintService invPrintService =
        new InventoryDocumentPrintService(
            sessionRepository, expectedItemRepository, scanActionRepository, pdfPrintFormService);
    assertThat(invPrintService.generateOpisPdf(sessionId).formCode()).isEqualTo("FT-9.13");
    assertThat(invPrintService.generateComparisonStatementPdf(sessionId).formCode())
        .isEqualTo("FT-9.13");
    assertThat(invPrintService.generateFinalActPdf(sessionId).formCode()).isEqualTo("FT-9.13");

    assertThatThrownBy(() -> invPrintService.signSessionPdf(sessionId, "invalid-doc-type", null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
