package uz.ttpu.inventory.finance.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WriteOffResultDto(
    UUID itemId,
    String inventoryNumber,
    String documentNumber,
    boolean dataWipeVerified,
    List<ReclaimedPartDto> reclaimedParts,
    Instant completedAt) {

  public WriteOffResultDto(
      UUID itemId,
      String inventoryNumber,
      String documentNumber,
      boolean dataWipeVerified,
      Instant completedAt) {
    this(itemId, inventoryNumber, documentNumber, dataWipeVerified, List.of(), completedAt);
  }
}
