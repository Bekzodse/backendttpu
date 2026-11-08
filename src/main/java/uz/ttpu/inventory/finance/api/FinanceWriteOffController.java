package uz.ttpu.inventory.finance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.finance.service.FinanceWriteOffService;

@RestController
@RequestMapping("/api/v1/finance/write-off")
@RequiredArgsConstructor
@Tag(
    name = "16. Finance Write-Off & Disposal",
    description =
        "Asset disposal, accounting balance sheet write-off, mandatory B5.6 IT data-wipe"
            + " certification, and usable parts reclamation back into warehouse stock.")
public class FinanceWriteOffController {

  private final FinanceWriteOffService writeOffService;

  @PostMapping("/{itemId}/complete")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'RECTOR', 'VICE_RECTOR')")
  @Operation(
      summary = "Complete balance sheet write-off for an asset",
      description =
          "Finalizes the asset write-off into HISOBDAN_CHIQARILGAN state. If the asset category is"
              + " computer, laptop, phone, server, or printer, hard-blocks completion unless a"
              + " data-wipe step is filled out. Allows receiving usable parts back into stock.")
  public ResponseEntity<WriteOffResultDto> completeWriteOff(
      @PathVariable UUID itemId, @Valid @RequestBody WriteOffCompletionRequest request) {
    return ResponseEntity.ok(writeOffService.completeWriteOff(itemId, request));
  }

  @PostMapping("/{itemId}/parts")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'RECTOR', 'VICE_RECTOR')")
  @Operation(
      summary = "Officially receive a usable part back into warehouse stock",
      description =
          "Receives a usable part salvaged from a written-off asset back into warehouse stock"
              + " (OMBORDA), maintaining a database link to the original written-off item's ID.")
  public ResponseEntity<ReclaimedPartDto> reclaimPart(
      @PathVariable UUID itemId,
      @Valid @RequestBody ReclaimablePartRequest request,
      Principal principal) {
    String username = principal != null ? principal.getName() : "system";
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(writeOffService.reclaimPart(itemId, request, username));
  }

  @GetMapping("/{itemId}/parts")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "List all reclaimed usable parts for a written-off asset",
      description =
          "Returns all usable parts officially received back into warehouse stock salvaged from"
              + " the specified written-off item.")
  public ResponseEntity<List<ReclaimedPartDto>> listReclaimedParts(@PathVariable UUID itemId) {
    return ResponseEntity.ok(writeOffService.listReclaimedParts(itemId));
  }
}
