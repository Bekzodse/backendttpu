package uz.ttpu.inventory.request.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.request.service.AssetRequestService;

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
@Tag(
        name = "08. Asset Requisition Requests",
        description = "Employee and department asset requisition requests (DRAFT -> SUBMITTED -> APPROVED/REJECTED -> FULFILLED) integrated with the Generic Workflow Engine."
)
public class AssetRequestController {

    private final AssetRequestService assetRequestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Create a new asset requisition request",
            description = "Creates a DRAFT requisition request for a category, quantity, and estimated unit cost, calculating totalEstimatedCost in NUMERIC(18,2)."
    )
    public AssetRequestDto createRequest(@Valid @RequestBody CreateAssetRequestDto request) {
        return assetRequestService.createRequest(request);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Submit a draft requisition request",
            description = "Transitions an asset requisition request from DRAFT to SUBMITTED so it can be routed through approval."
    )
    public AssetRequestDto submitRequest(@PathVariable UUID id) {
        return assetRequestService.submitRequest(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEPARTMENT_HEAD', 'DEAN_DEPARTMENT_HEAD', 'RECTOR', 'VICE_RECTOR', 'RECTORATE', 'WAREHOUSE_MANAGER')")
    @Operation(
            summary = "Approve a submitted requisition request",
            description = "Marks the requisition request as APPROVED and records the approving actor."
    )
    public AssetRequestDto approveRequest(@PathVariable UUID id, Principal principal) {
        String actor = principal != null ? principal.getName() : "SYSTEM";
        return assetRequestService.approveRequest(id, actor);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEPARTMENT_HEAD', 'DEAN_DEPARTMENT_HEAD', 'RECTOR', 'VICE_RECTOR', 'RECTORATE', 'WAREHOUSE_MANAGER')")
    @Operation(
            summary = "Reject a submitted requisition request",
            description = "Marks the requisition request as REJECTED and records the deciding actor."
    )
    public AssetRequestDto rejectRequest(@PathVariable UUID id, Principal principal) {
        String actor = principal != null ? principal.getName() : "SYSTEM";
        return assetRequestService.rejectRequest(id, actor);
    }

    @PostMapping("/{id}/fulfill")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER')")
    @Operation(
            summary = "Fulfill an approved requisition request",
            description = "Links an approved requisition request to the issued inventory item UUID and transitions its status to FULFILLED."
    )
    public AssetRequestDto fulfillRequest(
            @PathVariable UUID id,
            @RequestParam UUID fulfilledItemId,
            Principal principal
    ) {
        String actor = principal != null ? principal.getName() : "SYSTEM";
        return assetRequestService.fulfillRequest(id, fulfilledItemId, actor);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get asset requisition request by ID", description = "Retrieves a single asset requisition request by its UUID.")
    public AssetRequestDto getById(@PathVariable UUID id) {
        return assetRequestService.getById(id);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List all asset requisition requests", description = "Returns all asset requisition requests across all statuses.")
    public List<AssetRequestDto> listAll() {
        return assetRequestService.listAll();
    }
}
