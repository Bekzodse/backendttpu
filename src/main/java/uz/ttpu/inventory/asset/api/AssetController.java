package uz.ttpu.inventory.asset.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.asset.domain.IllegalStateTransitionException;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.asset.service.AssetService;
import uz.ttpu.inventory.common.domain.ErrorCode;
import uz.ttpu.inventory.common.domain.ProblemDetailFactory;

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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventory items retrieved"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized (ERR_AUTH_FAILED)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<List<ItemDto>> listItems(
            @Parameter(description = "Optional item lifecycle state filter") @RequestParam(required = false) ItemState state,
            @Parameter(description = "Optional department UUID filter") @RequestParam(required = false) UUID departmentId,
            @Parameter(description = "Optional building UUID filter") @RequestParam(required = false) UUID buildingId
    ) {
        return ResponseEntity.ok(assetService.listItems(state, departmentId, buildingId));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get item card by ID",
            description = "Retrieves full item details by UUID and automatically logs a READ audit record via ItemCardAuditAspect."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item card retrieved"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Item not found (ERR_RESOURCE_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<ItemDto> getItem(@PathVariable UUID id) {
        return ResponseEntity.ok(assetService.getItem(id));
    }

    @GetMapping("/{id}/export")
    @Operation(
            summary = "Export item card",
            description = "Exports an individual item card and logs an EXPORT audit entry in the audit_record table."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item card exported and audited"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Item not found (ERR_RESOURCE_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<ItemDto> exportItemCard(@PathVariable UUID id) {
        return ResponseEntity.ok(assetService.exportItemCard(id));
    }

    @PostMapping
    @Operation(
            summary = "Register a new inventory item",
            description = "Creates a new inventory item in OMBORDA state with a generated 10-character Base32 QR public ID and logs a CHANGE audit record."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Inventory item registered in OMBORDA state"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error (ERR_VALIDATION_FAILED)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<ItemDto> createItem(@Valid @RequestBody CreateItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assetService.createItem(request));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update item card metadata",
            description = "Updates item attributes, location, custodian, or financial values and logs a CHANGE audit record."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item card updated"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Item not found (ERR_RESOURCE_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item state transitioned successfully"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Item not found (ERR_RESOURCE_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Illegal state transition (ERR_ILLEGAL_ITEM_STATE_TRANSITION)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
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
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item state transition history retrieved")
    })
    public ResponseEntity<List<ItemStateHistoryDto>> getItemHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(assetService.getItemHistory(id));
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ProblemDetail> handleIllegalStateTransition(
            IllegalStateTransitionException ex,
            HttpServletRequest request
    ) {
        URI instance = (request != null && request.getRequestURI() != null && !request.getRequestURI().isBlank())
                ? URI.create(request.getRequestURI())
                : null;
        return ProblemDetailFactory.toResponseEntity(
                ErrorCode.ERR_ILLEGAL_ITEM_STATE_TRANSITION,
                ex.getMessage(),
                request != null ? request.getLocale() : null,
                instance
        );
    }
}
