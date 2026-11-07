package uz.ttpu.inventory.finance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.finance.service.FinanceWriteOffService;

@RestController
@RequestMapping("/api/v1/finance/write-off")
@RequiredArgsConstructor
@Tag(
    name = "16. Finance Write-Off & Disposal",
    description =
        "Asset disposal, accounting balance sheet write-off, and mandatory B5.6 IT data-wipe"
            + " certification.")
public class FinanceWriteOffController {

  private final FinanceWriteOffService writeOffService;

  @PostMapping("/{itemId}/complete")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'RECTOR', 'VICE_RECTOR')")
  @Operation(
      summary = "Complete balance sheet write-off for an asset",
      description =
          "Finalizes the asset write-off into HISOBDAN_CHIQARILGAN state. If the asset category is"
              + " computer, laptop, phone, server, or printer, hard-blocks completion unless a"
              + " data-wipe step is filled out.")
  public ResponseEntity<WriteOffResultDto> completeWriteOff(
      @PathVariable UUID itemId, @Valid @RequestBody WriteOffCompletionRequest request) {
    return ResponseEntity.ok(writeOffService.completeWriteOff(itemId, request));
  }
}
