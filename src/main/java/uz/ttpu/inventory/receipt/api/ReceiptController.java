package uz.ttpu.inventory.receipt.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.common.domain.ErrorCode;
import uz.ttpu.inventory.common.domain.ProblemDetailFactory;
import uz.ttpu.inventory.receipt.domain.DuplicateSerialNumberException;
import uz.ttpu.inventory.receipt.service.ReceiptService;

@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
@Tag(
    name = "05. Warehouse Receipts (Приход)",
    description =
        "Warehouse goods receipt documents (contracts, invoices, suppliers, receipt lines),"
            + " per-category serial number uniqueness enforcement, and receipt approval.")
public class ReceiptController {

  private final ReceiptService receiptService;

  @GetMapping
  @Operation(
      summary = "List all warehouse receipts",
      description =
          "Returns all draft and confirmed warehouse receipt documents ordered by creation date.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Warehouse receipts retrieved")})
  public ResponseEntity<List<ReceiptDto>> listReceipts() {
    return ResponseEntity.ok(receiptService.listReceipts());
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get warehouse receipt by ID",
      description = "Retrieves a warehouse receipt document and all of its line items by UUID.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Warehouse receipt retrieved"),
    @ApiResponse(
        responseCode = "404",
        description = "Warehouse receipt not found (ERR_RESOURCE_NOT_FOUND)",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<ReceiptDto> getReceipt(@PathVariable UUID id) {
    return ResponseEntity.ok(receiptService.getReceipt(id));
  }

  @PostMapping
  @Operation(
      summary = "Create a draft warehouse receipt",
      description =
          "Creates a DRAFT receipt with contract, invoice, supplier, and line items. Validates that"
              + " serial numbers are unique within each category (returning HTTP 409 on"
              + " duplicates).")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Draft warehouse receipt created"),
    @ApiResponse(
        responseCode = "400",
        description = "Validation error (ERR_VALIDATION_FAILED)",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Duplicate serial number in category (ERR_DUPLICATE_SERIAL_NUMBER)",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<ReceiptDto> createReceipt(
      @Valid @RequestBody CreateReceiptRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(receiptService.createReceipt(request));
  }

  @PostMapping("/{id}/approve")
  @Operation(
      summary = "Approve and confirm a warehouse receipt",
      description =
          "Transitions the receipt from DRAFT to CONFIRMED and publishes ReceiptApprovedEvent,"
              + " which automatically creates Item entities in OMBORDA state with random"
              + " 10-character Base32 QR IDs and enqueues a 1C outbox sync message.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Warehouse receipt approved and items created in OMBORDA state"),
    @ApiResponse(
        responseCode = "404",
        description = "Warehouse receipt not found (ERR_RESOURCE_NOT_FOUND)",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "Receipt already confirmed or duplicate serial number (ERR_STATE_CONFLICT /"
                + " ERR_DUPLICATE_SERIAL_NUMBER)",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<ReceiptDto> approveReceipt(@PathVariable UUID id) {
    return ResponseEntity.ok(receiptService.approveReceipt(id));
  }

  @ExceptionHandler(DuplicateSerialNumberException.class)
  public ResponseEntity<ProblemDetail> handleDuplicateSerialNumber(
      DuplicateSerialNumberException ex, HttpServletRequest request) {
    URI instance =
        (request != null && request.getRequestURI() != null && !request.getRequestURI().isBlank())
            ? URI.create(request.getRequestURI())
            : null;
    return ProblemDetailFactory.toResponseEntity(
        ErrorCode.ERR_DUPLICATE_SERIAL_NUMBER,
        ex.getMessage(),
        request != null ? request.getLocale() : null,
        instance);
  }
}
