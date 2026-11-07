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
    description =
        "Core inventory items, 8-state lifecycle state machine (ItemStateService), state transition"
            + " history, and audited item card operations.")
public class AssetController {

  private final AssetService assetService;

  @GetMapping
  @Operation(
      summary = "List inventory items",
      description =
          "Returns inventory items optionally filtered by lifecycle state (OMBORDA, YOLDA,"
              + " FOYDALANISHDA, VAQTINCHA_TASHQARIDA, TAMIRDA, QIDIRUVDA,"
              + " HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN, HISOBDAN_CHIQARILGAN), departmentId, and"
              + " buildingId.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Inventory items retrieved"),
    @ApiResponse(
        responseCode = "401",
        description = "Unauthorized (ERR_AUTH_FAILED)",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<List<ItemDto>> listItems(
      @Parameter(description = "Optional item lifecycle state filter")
          @RequestParam(required = false)
          ItemState state,
      @Parameter(description = "Optional department UUID filter") @RequestParam(required = false)
          UUID departmentId,
      @Parameter(description = "Optional building UUID filter") @RequestParam(required = false)
          UUID buildingId) {
    return ResponseEntity.ok(assetService.listItems(state, departmentId, buildingId));
  }

  @GetMapping("/search")
  @Operation(
      summary = "High-performance pg_trgm full-text & fuzzy asset search (FT-8.2, FT-8.3)",
      description =
          "Executes dynamic Spring Data JPA Specification queries backed by PostgreSQL pg_trgm GIN"
              + " trigram indexes across name, inventory_number, serial, model, responsible_person,"
              + " and room. Designed to return results in under 3 seconds across 200,000+ items.")
  public ResponseEntity<AssetSearchResultDto> searchItems(
      @Parameter(
              description =
                  "Global full-text / fuzzy search term across name, inventoryNumber, serial,"
                      + " model, responsiblePerson, and room")
          @RequestParam(required = false)
          String q,
      @Parameter(description = "Filter by asset name") @RequestParam(required = false) String name,
      @Parameter(description = "Filter by inventory number") @RequestParam(required = false)
          String inventoryNumber,
      @Parameter(description = "Filter by serial number") @RequestParam(required = false)
          String serial,
      @Parameter(description = "Filter by model") @RequestParam(required = false) String model,
      @Parameter(description = "Filter by responsible person name or code")
          @RequestParam(required = false)
          String responsiblePerson,
      @Parameter(description = "Filter by room number or name") @RequestParam(required = false)
          String room,
      @Parameter(description = "Filter by lifecycle state") @RequestParam(required = false)
          ItemState state,
      @Parameter(description = "Filter by category UUID") @RequestParam(required = false)
          UUID categoryId,
      @Parameter(description = "Filter by department UUID") @RequestParam(required = false)
          UUID departmentId,
      @Parameter(description = "Filter by building UUID") @RequestParam(required = false)
          UUID buildingId,
      @Parameter(description = "Filter by room UUID") @RequestParam(required = false) UUID roomId,
      @Parameter(description = "Filter by custodian employee UUID") @RequestParam(required = false)
          UUID custodianEmployeeId,
      @Parameter(description = "Enable pg_trgm word_similarity typo-tolerant matching")
          @RequestParam(defaultValue = "false")
          boolean fuzzy,
      @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Page size (max 200)") @RequestParam(defaultValue = "50") int size) {
    AssetSearchFilterRequest filter =
        new AssetSearchFilterRequest(
            q,
            name,
            inventoryNumber,
            serial,
            model,
            responsiblePerson,
            room,
            state,
            categoryId,
            departmentId,
            buildingId,
            roomId,
            custodianEmployeeId,
            fuzzy);
    return ResponseEntity.ok(assetService.searchItems(filter, page, size));
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get item card by ID",
      description =
          "Retrieves full item details by UUID and automatically logs a READ audit record via"
              + " ItemCardAuditAspect.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Item card retrieved"),
    @ApiResponse(
        responseCode = "404",
        description = "Item not found (ERR_RESOURCE_NOT_FOUND)",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<ItemDto> getItem(@PathVariable UUID id) {
    return ResponseEntity.ok(assetService.getItem(id));
  }

  @GetMapping("/{id}/export")
  @Operation(
      summary = "Export item card",
      description =
          "Exports an individual item card and logs an EXPORT audit entry in the audit_record"
              + " table.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Item card exported and audited"),
    @ApiResponse(
        responseCode = "404",
        description = "Item not found (ERR_RESOURCE_NOT_FOUND)",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<ItemDto> exportItemCard(@PathVariable UUID id) {
    return ResponseEntity.ok(assetService.exportItemCard(id));
  }

  @PostMapping
  @Operation(
      summary = "Register a new inventory item",
      description =
          "Creates a new inventory item in OMBORDA state with a generated 10-character Base32 QR"
              + " public ID and logs a CHANGE audit record.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Inventory item registered in OMBORDA state"),
    @ApiResponse(
        responseCode = "400",
        description = "Validation error (ERR_VALIDATION_FAILED)",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<ItemDto> createItem(@Valid @RequestBody CreateItemRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(assetService.createItem(request));
  }

  @PutMapping("/{id}")
  @Operation(
      summary = "Update item card metadata",
      description =
          "Updates item attributes, location, custodian, or financial values and logs a CHANGE"
              + " audit record.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Item card updated"),
    @ApiResponse(
        responseCode = "404",
        description = "Item not found (ERR_RESOURCE_NOT_FOUND)",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<ItemDto> updateItem(
      @PathVariable UUID id, @Valid @RequestBody CreateItemRequest request) {
    return ResponseEntity.ok(assetService.updateItem(id, request));
  }

  @PostMapping("/{id}/transitions")
  @Operation(
      summary = "Execute a validated ItemState transition",
      description =
          "Validates and applies an ItemEvent against the strict 8-state x 12-event state"
              + " transition matrix in ItemStateService, persists ItemStateHistory, and publishes"
              + " ItemStateChangedEvent.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Item state transitioned successfully"),
    @ApiResponse(
        responseCode = "404",
        description = "Item not found (ERR_RESOURCE_NOT_FOUND)",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Illegal state transition (ERR_ILLEGAL_ITEM_STATE_TRANSITION)",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ResponseEntity<ItemDto> transitionItemState(
      @PathVariable UUID id, @Valid @RequestBody TransitionItemRequest request) {
    return ResponseEntity.ok(assetService.transitionItemState(id, request));
  }

  @GetMapping("/{id}/history")
  @Operation(
      summary = "Get item state transition history",
      description =
          "Returns the chronological list of all state transitions (fromState, toState, event,"
              + " documentRef, transitionedAt) for the item.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Item state transition history retrieved")
  })
  public ResponseEntity<List<ItemStateHistoryDto>> getItemHistory(@PathVariable UUID id) {
    return ResponseEntity.ok(assetService.getItemHistory(id));
  }

  @PostMapping("/{id}/data-wipe")
  @Operation(
      summary = "Record mandatory IT asset data-wipe certification",
      description =
          "Captures data-wipe details (performer name, method, certificate, notes, timestamp) for"
              + " computers, laptops, phones, servers, and printers prior to write-off completion.")
  public ResponseEntity<DataWipeRecordDto> recordDataWipe(
      @PathVariable UUID id, @Valid @RequestBody RecordDataWipeRequest request) {
    return ResponseEntity.ok(assetService.recordDataWipe(id, request));
  }

  @GetMapping("/{id}/data-wipe")
  @Operation(
      summary = "Get latest IT asset data-wipe certification",
      description = "Retrieves the most recent data-wipe certification recorded for the asset.")
  public ResponseEntity<DataWipeRecordDto> getLatestDataWipe(@PathVariable UUID id) {
    return assetService
        .getLatestDataWipe(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @ExceptionHandler(IllegalStateTransitionException.class)
  public ResponseEntity<ProblemDetail> handleIllegalStateTransition(
      IllegalStateTransitionException ex, HttpServletRequest request) {
    URI instance =
        (request != null && request.getRequestURI() != null && !request.getRequestURI().isBlank())
            ? URI.create(request.getRequestURI())
            : null;
    return ProblemDetailFactory.toResponseEntity(
        ErrorCode.ERR_ILLEGAL_ITEM_STATE_TRANSITION,
        ex.getMessage(),
        request != null ? request.getLocale() : null,
        instance);
  }
}
