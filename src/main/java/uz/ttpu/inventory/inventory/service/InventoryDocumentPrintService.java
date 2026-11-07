package uz.ttpu.inventory.inventory.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.inventory.api.InventoryPdfDocumentDto;
import uz.ttpu.inventory.inventory.api.InventorySignPdfRequest;
import uz.ttpu.inventory.inventory.domain.InventoryExpectedItem;
import uz.ttpu.inventory.inventory.domain.InventoryLineStatus;
import uz.ttpu.inventory.inventory.domain.InventoryScanAction;
import uz.ttpu.inventory.inventory.domain.InventorySession;
import uz.ttpu.inventory.inventory.domain.ReconciliationCategory;
import uz.ttpu.inventory.inventory.repository.InventoryExpectedItemRepository;
import uz.ttpu.inventory.inventory.repository.InventoryScanActionRepository;
import uz.ttpu.inventory.inventory.repository.InventorySessionRepository;
import uz.ttpu.inventory.report.api.GeneratedPdfDocumentDto;
import uz.ttpu.inventory.report.api.InventorySessionPrintRequest;
import uz.ttpu.inventory.report.api.InventorySessionPrintRequest.ExpectedItemPrintRow;
import uz.ttpu.inventory.report.api.InventorySessionPrintRequest.ReconciledItemPrintRow;
import uz.ttpu.inventory.report.api.PrintFormApi;

@Service
@RequiredArgsConstructor
public class InventoryDocumentPrintService {

  private final InventorySessionRepository sessionRepository;
  private final InventoryExpectedItemRepository expectedItemRepository;
  private final InventoryScanActionRepository scanActionRepository;
  private final PrintFormApi printFormApi;

  @Transactional(readOnly = true)
  public InventoryPdfDocumentDto generateOpisPdf(UUID sessionId) {
    InventorySessionPrintRequest printReq = buildSessionPrintRequest(sessionId);
    return toInventoryPdfDto(printFormApi.generateInventoryOpisPdf(printReq));
  }

  @Transactional(readOnly = true)
  public InventoryPdfDocumentDto generateComparisonStatementPdf(UUID sessionId) {
    InventorySessionPrintRequest printReq = buildSessionPrintRequest(sessionId);
    return toInventoryPdfDto(printFormApi.generateInventoryComparisonStatementPdf(printReq));
  }

  @Transactional(readOnly = true)
  public InventoryPdfDocumentDto generateFinalActPdf(UUID sessionId) {
    InventorySessionPrintRequest printReq = buildSessionPrintRequest(sessionId);
    return toInventoryPdfDto(printFormApi.generateInventoryFinalActPdf(printReq));
  }

  @Transactional
  public InventoryPdfDocumentDto signSessionPdf(
      UUID sessionId, String documentTypeKey, InventorySignPdfRequest request) {
    InventorySessionPrintRequest printReq = buildSessionPrintRequest(sessionId);
    String normalized =
        documentTypeKey == null ? "" : documentTypeKey.trim().toLowerCase(Locale.ROOT);

    GeneratedPdfDocumentDto baseDoc =
        switch (normalized) {
          case "opis", "inventory-opis" -> printFormApi.generateInventoryOpisPdf(printReq);
          case "comparison-statement", "comparison" ->
              printFormApi.generateInventoryComparisonStatementPdf(printReq);
          case "final-act", "final" -> printFormApi.generateInventoryFinalActPdf(printReq);
          default ->
              throw new IllegalArgumentException(
                  "Unsupported inventory PDF document type '"
                      + documentTypeKey
                      + "'. Expected one of: opis, comparison-statement, final-act");
        };

    GeneratedPdfDocumentDto signedDoc =
        printFormApi.attachEImzoSignatureToPdf(
            baseDoc,
            request.signerPinfl(),
            request.signerTin(),
            request.signerFullName(),
            request.signerOrganization(),
            request.certificateSerialNumber());
    return toInventoryPdfDto(signedDoc);
  }

  private InventorySessionPrintRequest buildSessionPrintRequest(UUID sessionId) {
    InventorySession session =
        sessionRepository
            .findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("InventorySession", sessionId));

    List<InventoryExpectedItem> expectedEntities =
        expectedItemRepository.findAllBySessionIdOrderByInventoryNumberAsc(sessionId);

    List<ExpectedItemPrintRow> expectedRows = new ArrayList<>();
    List<ReconciledItemPrintRow> reconciledRows = new ArrayList<>();

    int joyidaCount = 0;
    int boshqaXonadaCount = 0;
    int topilmadiCount = 0;

    for (InventoryExpectedItem expected : expectedEntities) {
      expectedRows.add(
          new ExpectedItemPrintRow(
              expected.getItemId(),
              expected.getInventoryNumber(),
              expected.getQrCode(),
              expected.getItemName(),
              expected.getRoomId(),
              expected.getScannedRoomId(),
              expected.getCustodianEmployeeId(),
              expected.getBookValue(),
              expected.getLineStatus().name(),
              expected.getScannedAt()));

      if (expected.getLineStatus() == InventoryLineStatus.FOUND) {
        joyidaCount++;
        reconciledRows.add(
            new ReconciledItemPrintRow(
                expected.getItemId(),
                expected.getInventoryNumber(),
                expected.getQrCode(),
                expected.getItemName(),
                expected.getRoomId(),
                expected.getScannedRoomId(),
                ReconciliationCategory.JOYIDA.name(),
                ReconciliationCategory.JOYIDA.getLabel(),
                false,
                expected.getScannedAt()));
      } else if (expected.getLineStatus() == InventoryLineStatus.MISPLACED_FOUND) {
        boshqaXonadaCount++;
        reconciledRows.add(
            new ReconciledItemPrintRow(
                expected.getItemId(),
                expected.getInventoryNumber(),
                expected.getQrCode(),
                expected.getItemName(),
                expected.getRoomId(),
                expected.getScannedRoomId(),
                ReconciliationCategory.BOSHQA_XONADA_TOPILDI.name(),
                ReconciliationCategory.BOSHQA_XONADA_TOPILDI.getLabel(),
                false,
                expected.getScannedAt()));
      } else {
        topilmadiCount++;
        reconciledRows.add(
            new ReconciledItemPrintRow(
                expected.getItemId(),
                expected.getInventoryNumber(),
                expected.getQrCode(),
                expected.getItemName(),
                expected.getRoomId(),
                null,
                ReconciliationCategory.TOPILMADI.name(),
                ReconciliationCategory.TOPILMADI.getLabel(),
                true,
                null));
      }
    }

    int ortiqchaCount = 0;
    if (scanActionRepository != null) {
      List<InventoryScanAction> surplusActions =
          scanActionRepository.findAllBySessionIdAndReconciliationCategoryOrderByScannedAtAsc(
              sessionId, ReconciliationCategory.ORTIQCHA);
      Set<String> seenSurplusKeys = new LinkedHashSet<>();
      for (InventoryScanAction surplus : surplusActions) {
        String dedupKey =
            surplus.getItemId() != null
                ? "ID:" + surplus.getItemId()
                : (surplus.getQrCode() != null && !surplus.getQrCode().isBlank()
                    ? "QR:" + surplus.getQrCode().toUpperCase(Locale.ROOT)
                    : "ACT:" + surplus.getId());
        if (seenSurplusKeys.add(dedupKey)) {
          ortiqchaCount++;
          reconciledRows.add(
              new ReconciledItemPrintRow(
                  surplus.getItemId(),
                  surplus.getInventoryNumber(),
                  surplus.getQrCode(),
                  surplus.getItemName(),
                  surplus.getExpectedRoomId(),
                  surplus.getScannedRoomId(),
                  ReconciliationCategory.ORTIQCHA.name(),
                  ReconciliationCategory.ORTIQCHA.getLabel(),
                  false,
                  surplus.getScannedAt()));
        }
      }
    }

    return new InventorySessionPrintRequest(
        session.getId(),
        session.getSessionCode(),
        session.getRectorOrderNumber(),
        session.getRectorOrderDate(),
        session.getSessionDate(),
        session.getStatus().name(),
        session.getChairmanEmployeeId(),
        session.getCommissionMemberIds(),
        session.getRoomIds(),
        joyidaCount,
        boshqaXonadaCount,
        topilmadiCount,
        ortiqchaCount,
        expectedRows,
        reconciledRows);
  }

  private InventoryPdfDocumentDto toInventoryPdfDto(GeneratedPdfDocumentDto doc) {
    int sigCount = doc.eimzoSignatures() != null ? doc.eimzoSignatures().size() : 0;
    String latestPkcs7 = (sigCount > 0) ? doc.eimzoSignatures().get(0).pkcs7SignatureId() : "";
    return new InventoryPdfDocumentDto(
        doc.pdfBytes(),
        doc.fileName(),
        doc.formCode(),
        doc.documentType(),
        doc.documentId(),
        doc.documentNumber(),
        doc.sha256Checksum(),
        sigCount,
        latestPkcs7,
        doc.generatedAt());
  }
}
