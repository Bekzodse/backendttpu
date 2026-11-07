package uz.ttpu.inventory.asset.api;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.asset.domain.IllegalStateTransitionException;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.asset.service.AssetService;

@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
@Tag(
        name = "03. Asset Management & Item State Machine",
        description = "Core inventory items, 8-state lifecycle state machine (ItemStateService), state transition history, and audited item card operations."
)
public class AssetController {

    private final AssetService assetService;

    @GetMapping
    @Operation(
            summary = "List inventory items",
            description = "Returns inventory items optionally filtered by lifecycle state (OMBORDA, YOLDA, FOYDALANISHDA, VAQTINCHA_TASHQARIDA, TAMIRDA, QIDIRUVDA, HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN, HISOBDAN_CHIQARILGAN), departmentId, and buildingId."
    )
    public ResponseEntity<List<ItemDto>> listItems(
            @RequestParam(required = false) ItemState state,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID buildingId
    ) {
        return ResponseEntity.ok(assetService.listItems(state, departmentId, buildingId));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get item card by ID",
            description = "Retrieves full item details by UUID and automatically logs a READ audit record via ItemCardAuditAspect."
    )
    public ResponseEntity<ItemDto> getItem(@PathVariable UUID id) {
        return ResponseEntity.ok(assetService.getItem(id));
    }

    @GetMapping("/{id}/export")
    @Operation(
            summary = "Export item card",
            description = "Exports an individual item card and logs an EXPORT audit entry in the audit_record table."
    )
    public ResponseEntity<ItemDto> exportItemCard(@PathVariable UUID id) {
        return ResponseEntity.ok(assetService.exportItemCard(id));
    }

    @PostMapping
    @Operation(
            summary = "Register a new inventory item",
            description = "Creates a new inventory item in OMBORDA state with a generated 10-character Base32 QR public ID and logs a CHANGE audit record."
    )
    public ResponseEntity<ItemDto> createItem(@Valid @RequestBody CreateItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assetService.createItem(request));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update item card metadata",
            description = "Updates item attributes, location, custodian, or financial values and logs a CHANGE audit record."
    )
    public ResponseEntity<ItemDto> updateItem(
            @PathVariable UUID id,
            @Valid @RequestBody CreateItemRequest request
    ) {
        return ResponseEntity.ok(assetService.updateItem(id, request));
    }

    @PostMapping("/{id}/transitions")
    @Operation(
            summary = "Execute a validated ItemState transition",
            description = "Validates and applies an ItemEvent against the strict 8-state x 12-event state transition matrix in ItemStateService, persists ItemStateHistory, and publishes ItemStateChangedEvent."
    )
    public ResponseEntity<ItemDto> transitionItemState(
            @PathVariable UUID id,
            @Valid @RequestBody TransitionItemRequest request
    ) {
        return ResponseEntity.ok(assetService.transitionItemState(id, request));
    }

    @GetMapping("/{id}/history")
    @Operation(
            summary = "Get item state transition history",
            description = "Returns the chronological list of all state transitions (fromState, toState, event, documentRef, transitionedAt) for the item."
    )
    public ResponseEntity<List<ItemStateHistoryDto>> getItemHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(assetService.getItemHistory(id));
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ProblemDetail> handleIllegalStateTransition(IllegalStateTransitionException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Illegal Item State Transition");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
