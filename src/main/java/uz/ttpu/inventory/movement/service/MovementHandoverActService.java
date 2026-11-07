package uz.ttpu.inventory.movement.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.movement.api.MovementHandoverActPdfDto;
import uz.ttpu.inventory.movement.api.MovementSignPdfRequest;
import uz.ttpu.inventory.movement.domain.MovementRecord;
import uz.ttpu.inventory.movement.repository.MovementRepository;
import uz.ttpu.inventory.report.api.GeneratedPdfDocumentDto;
import uz.ttpu.inventory.report.api.HandoverActPrintRequest;
import uz.ttpu.inventory.report.api.PrintFormApi;

@Service
@RequiredArgsConstructor
public class MovementHandoverActService {

  private final MovementRepository movementRepository;
  private final AssetLogisticsApi assetLogisticsApi;
  private final PrintFormApi printFormApi;

  @Transactional(readOnly = true)
  public MovementHandoverActPdfDto generateHandoverActPdf(UUID operationId) {
    GeneratedPdfDocumentDto generated = buildRawHandoverActDocument(operationId);
    return toMovementPdfDto(generated);
  }

  @Transactional
  public MovementHandoverActPdfDto signHandoverActPdf(
      UUID operationId, MovementSignPdfRequest request) {
    GeneratedPdfDocumentDto unsignedOrCurrent = buildRawHandoverActDocument(operationId);
    GeneratedPdfDocumentDto signed =
        printFormApi.attachEImzoSignatureToPdf(
            unsignedOrCurrent,
            request.signerPinfl(),
            request.signerTin(),
            request.signerFullName(),
            request.signerOrganization(),
            request.certificateSerialNumber());
    return toMovementPdfDto(signed);
  }

  private GeneratedPdfDocumentDto buildRawHandoverActDocument(UUID operationId) {
    List<MovementRecord> records =
        movementRepository.findAllByOperationIdOrderByRecordedAtAsc(operationId);
    if (records.isEmpty()) {
      throw new ResourceNotFoundException("Movement operation", operationId);
    }
    MovementRecord latest = records.get(records.size() - 1);
    ItemDto item = assetLogisticsApi.resolveItemByIdOrQr(latest.getItemId(), null);

    BigDecimal bookValue =
        item.residualValue() != null
            ? item.residualValue()
            : (item.purchasePrice() != null ? item.purchasePrice() : BigDecimal.ZERO);

    HandoverActPrintRequest printRequest =
        new HandoverActPrintRequest(
            operationId,
            latest.getDocumentNumber(),
            latest.getMovementType().name(),
            latest.getEventType().name(),
            item.id(),
            item.inventoryNumber(),
            item.qrCode(),
            item.name(),
            item.serialNumber(),
            "CONFIRMED".equals(latest.getEventType().name()) ? "FOYDALANISHDA" : "YOLDA",
            bookValue,
            latest.getFromBuildingId(),
            latest.getToBuildingId(),
            latest.getFromRoomId(),
            latest.getToRoomId(),
            latest.getFromDepartmentId(),
            latest.getToDepartmentId(),
            latest.getFromEmployeeId(),
            latest.getToEmployeeId(),
            latest.getActorEmployeeId(),
            latest.isTotpVerified(),
            latest.getReason(),
            latest.getRecordedAt());

    return printFormApi.generateHandoverActPdf(printRequest);
  }

  private MovementHandoverActPdfDto toMovementPdfDto(GeneratedPdfDocumentDto doc) {
    int sigCount = doc.eimzoSignatures() != null ? doc.eimzoSignatures().size() : 0;
    String latestPkcs7 = (sigCount > 0) ? doc.eimzoSignatures().get(0).pkcs7SignatureId() : "";
    return new MovementHandoverActPdfDto(
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
