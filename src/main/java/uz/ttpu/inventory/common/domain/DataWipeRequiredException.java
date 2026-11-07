package uz.ttpu.inventory.common.domain;

import java.util.UUID;
import lombok.Getter;

@Getter
public class DataWipeRequiredException extends RuntimeException {

  private final UUID itemId;
  private final String inventoryNumber;
  private final String categoryName;

  public DataWipeRequiredException(UUID itemId, String inventoryNumber, String categoryName) {
    super(
        String.format(
            "Mandatory IT data-wipe step required for asset '%s' (category: %s, ID: %s) before"
                + " write-off completion. Capturing performer name and wipe method is mandatory.",
            inventoryNumber, categoryName, itemId));
    this.itemId = itemId;
    this.inventoryNumber = inventoryNumber;
    this.categoryName = categoryName;
  }
}
