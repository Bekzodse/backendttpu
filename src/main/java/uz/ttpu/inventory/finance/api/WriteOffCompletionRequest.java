package uz.ttpu.inventory.finance.api;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record WriteOffCompletionRequest(
    @NotBlank String documentNumber,
    String reason,
    String performerName,
    String wipeMethod,
    List<ReclaimablePartRequest> usableParts) {

  public WriteOffCompletionRequest(
      String documentNumber, String reason, String performerName, String wipeMethod) {
    this(documentNumber, reason, performerName, wipeMethod, List.of());
  }
}
