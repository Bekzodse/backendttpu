package uz.ttpu.inventory.movement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.movement.service.MovementHandoverActService;
import uz.ttpu.inventory.movement.service.MovementService;

@RestController
@RequestMapping("/api/v1/movements")
@RequiredArgsConstructor
@Tag(
    name = "09. Internal Logistics: Issue, Transfer & FT-12.9 Handover Act PDF",
    description =
        "Warehouse issue (OMBORDA -> YOLDA -> FOYDALANISHDA), room-to-room / person-to-person"
            + " transfer flows with recipient 2FA TOTP confirmation, insert-only movement ledger,"
            + " and official FT-12.9 Handover Act (Dalolatnoma) PDF generation with E-IMZO"
            + " signatures.")
public class MovementController {

  private final MovementService movementService;
  private final MovementHandoverActService handoverActService;

  @PostMapping("/issues")
  @Operation(
      summary = "Dispatch an item from warehouse (Issue initiation)",
      description =
          "Warehouse scans an item (by UUID or 10-character QR code) and assigns a target room and"
              + " recipient employee. Moves the item state from OMBORDA to YOLDA and appends an"
              + " ISSUE_DISPATCHED row to the insert-only movements ledger.")
  public ResponseEntity<MovementRecordDto> dispatchIssue(
      @Valid @RequestBody IssueDispatchRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(movementService.dispatchIssue(request));
  }

  @PostMapping("/issues/{operationId}/confirm")
  @Operation(
      summary = "Confirm warehouse issue receipt via 2FA TOTP",
      description =
          "Recipient employee confirms receipt using a valid 6-digit TOTP code. Transitions the"
              + " item from YOLDA to FOYDALANISHDA, updates its location and custodian, and appends"
              + " an ISSUE_ACCEPTED movement record.")
  public ResponseEntity<MovementRecordDto> confirmIssue(
      @PathVariable UUID operationId, @Valid @RequestBody MovementConfirmRequest request) {
    return ResponseEntity.ok(movementService.confirmIssue(operationId, request));
  }

  @PostMapping("/issues/{operationId}/reject")
  @Operation(
      summary = "Reject warehouse issue and return item to OMBORDA",
      description =
          "Recipient rejects the dispatched item with a reason. Returns the item state from YOLDA"
              + " back to OMBORDA and appends an ISSUE_REJECTED movement record.")
  public ResponseEntity<MovementRecordDto> rejectIssue(
      @PathVariable UUID operationId, @Valid @RequestBody MovementRejectRequest request) {
    return ResponseEntity.ok(movementService.rejectIssue(operationId, request));
  }

  @PostMapping("/transfers")
  @Operation(
      summary = "Initiate room-to-room or person-to-person transfer",
      description =
          "Initiates a TRANSFER_ROOM_TO_ROOM or TRANSFER_PERSON_TO_PERSON operation. Transitions"
              + " the item from FOYDALANISHDA to YOLDA and appends a TRANSFER_INITIATED movement"
              + " record.")
  public ResponseEntity<MovementRecordDto> initiateTransfer(
      @Valid @RequestBody TransferInitiateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(movementService.initiateTransfer(request));
  }

  @PostMapping("/transfers/{operationId}/confirm")
  @Operation(
      summary = "Confirm transfer receipt via 2FA TOTP",
      description =
          "Receiving employee confirms the transfer with a 6-digit TOTP code. Transitions the item"
              + " from YOLDA to FOYDALANISHDA, assigns the new room/custodian, and appends a"
              + " TRANSFER_ACCEPTED movement record.")
  public ResponseEntity<MovementRecordDto> confirmTransfer(
      @PathVariable UUID operationId, @Valid @RequestBody MovementConfirmRequest request) {
    return ResponseEntity.ok(movementService.confirmTransfer(operationId, request));
  }

  @PostMapping("/transfers/{operationId}/reject")
  @Operation(
      summary = "Reject transfer and restore item to original custodian",
      description =
          "Receiving employee rejects the transfer with a reason. Restores the item from YOLDA back"
              + " to FOYDALANISHDA under the original room/custodian and appends a"
              + " TRANSFER_REJECTED movement record.")
  public ResponseEntity<MovementRecordDto> rejectTransfer(
      @PathVariable UUID operationId, @Valid @RequestBody MovementRejectRequest request) {
    return ResponseEntity.ok(movementService.rejectTransfer(operationId, request));
  }

  @GetMapping("/operations/{operationId}")
  @Operation(
      summary = "Get movement ledger records by operation ID",
      description =
          "Returns the chronological sequence of insert-only movement records for a specific issue"
              + " or transfer operationId.")
  public ResponseEntity<List<MovementRecordDto>> getOperationRecords(
      @PathVariable UUID operationId) {
    return ResponseEntity.ok(movementService.getOperationRecords(operationId));
  }

  @GetMapping(
      value = "/operations/{operationId}/handover-act.pdf",
      produces = MediaType.APPLICATION_PDF_VALUE)
  @Operation(
      summary = "Generate official FT-12.9 Handover Act (Dalolatnoma) PDF for an issue or transfer",
      description =
          "Renders the editable FT-12.9 XHTML template via Thymeleaf + OpenHTMLtoPDF for the"
              + " specified movement operationId and embeds E-IMZO signature metadata into the"
              + " PDF.")
  public ResponseEntity<byte[]> generateHandoverActPdf(@PathVariable UUID operationId) {
    MovementHandoverActPdfDto pdf = handoverActService.generateHandoverActPdf(operationId);
    return toPdfResponse(pdf);
  }

  @PostMapping(
      value = "/operations/{operationId}/handover-act/sign",
      produces = MediaType.APPLICATION_PDF_VALUE)
  @Operation(
      summary = "Attach E-IMZO PKCS#7 digital signature metadata to FT-12.9 Handover Act PDF",
      description =
          "Verifies the signer's E-IMZO PKCS#7 signature, persists an EImzoSignatureRecord, and"
              + " embeds the verified signature metadata directly into the FT-12.9 Handover Act"
              + " PDF.")
  public ResponseEntity<byte[]> signHandoverActPdf(
      @PathVariable UUID operationId, @Valid @RequestBody MovementSignPdfRequest request) {
    MovementHandoverActPdfDto signedPdf =
        handoverActService.signHandoverActPdf(operationId, request);
    return toPdfResponse(signedPdf);
  }

  @GetMapping("/items/{itemId}")
  @Operation(
      summary = "Get full movement history for an item",
      description =
          "Returns all historical issue and transfer records for the specified item UUID ordered"
              + " from newest to oldest.")
  public ResponseEntity<List<MovementRecordDto>> getItemMovementHistory(@PathVariable UUID itemId) {
    return ResponseEntity.ok(movementService.getItemMovementHistory(itemId));
  }

  private ResponseEntity<byte[]> toPdfResponse(MovementHandoverActPdfDto pdf) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + pdf.fileName() + "\"")
        .header("X-Form-Code", pdf.formCode())
        .header("X-Document-Type", pdf.documentType())
        .header("X-Document-SHA256", pdf.sha256Checksum())
        .header("X-EIMZO-Signature-Count", String.valueOf(pdf.eimzoSignatureCount()))
        .header("X-EIMZO-PKCS7-Id", pdf.latestPkcs7SignatureId())
        .body(pdf.pdfBytes());
  }
}
