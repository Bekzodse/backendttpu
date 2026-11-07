package uz.ttpu.inventory.receipt.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.receipt.domain.DuplicateSerialNumberException;
import uz.ttpu.inventory.receipt.service.ReceiptService;

@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
@Tag(
        name = "05. Warehouse Receipts (Приход)",
        description = "Warehouse goods receipt documents (contracts, invoices, suppliers, receipt lines), per-category serial number uniqueness enforcement, and receipt approval."
)
public class ReceiptController {

    private final ReceiptService receiptService;

    @GetMapping
    @Operation(summary = "List all warehouse receipts", description = "Returns all draft and confirmed warehouse receipt documents ordered by creation date.")
    public ResponseEntity<List<ReceiptDto>> listReceipts() {
        return ResponseEntity.ok(receiptService.listReceipts());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get warehouse receipt by ID", description = "Retrieves a warehouse receipt document and all of its line items by UUID.")
    public ResponseEntity<ReceiptDto> getReceipt(@PathVariable UUID id) {
        return ResponseEntity.ok(receiptService.getReceipt(id));
    }

    @PostMapping
    @Operation(
            summary = "Create a draft warehouse receipt",
            description = "Creates a DRAFT receipt with contract, invoice, supplier, and line items. Validates that serial numbers are unique within each category (returning HTTP 409 on duplicates)."
    )
    public ResponseEntity<ReceiptDto> createReceipt(@Valid @RequestBody CreateReceiptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(receiptService.createReceipt(request));
    }

    @PostMapping("/{id}/approve")
    @Operation(
            summary = "Approve and confirm a warehouse receipt",
            description = "Transitions the receipt from DRAFT to CONFIRMED and publishes ReceiptApprovedEvent, which automatically creates Item entities in OMBORDA state with random 10-character Base32 QR IDs and enqueues a 1C outbox sync message."
    )
    public ResponseEntity<ReceiptDto> approveReceipt(@PathVariable UUID id) {
        return ResponseEntity.ok(receiptService.approveReceipt(id));
    }

    @ExceptionHandler(DuplicateSerialNumberException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateSerialNumber(DuplicateSerialNumberException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Duplicate Serial Number in Category");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
