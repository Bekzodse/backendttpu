package uz.ttpu.inventory.common.domain;

import java.util.Collections;
import java.util.List;
import lombok.Getter;

@Getter
public class ClearanceChecklistBlockedException extends RuntimeException {

  private final String checklistNumber;
  private final long remainingItemsCount;
  private final List<String> unreturnedItemSummaries;

  public ClearanceChecklistBlockedException(
      String checklistNumber, long remainingItemsCount, List<String> unreturnedItemSummaries) {
    super(
        String.format(
            "Clearance checklist '%s' cannot be closed. Employee still has %d assigned inventory"
                + " item(s) to return or transfer: %s",
            checklistNumber,
            remainingItemsCount,
            unreturnedItemSummaries != null ? String.join(", ", unreturnedItemSummaries) : "none"));
    this.checklistNumber = checklistNumber;
    this.remainingItemsCount = remainingItemsCount;
    this.unreturnedItemSummaries =
        unreturnedItemSummaries != null
            ? List.copyOf(unreturnedItemSummaries)
            : Collections.emptyList();
  }
}
