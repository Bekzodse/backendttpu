package uz.ttpu.inventory.report.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventorySessionPrintRequest(
    UUID sessionId,
    String sessionCode,
    String rectorOrderNumber,
    Instant rectorOrderDate,
    Instant sessionDate,
    String sessionStatus,
    UUID chairmanEmployeeId,
    List<UUID> commissionMemberIds,
    List<UUID> roomIds,
    int joyidaCount,
    int boshqaXonadaCount,
    int topilmadiCount,
    int ortiqchaCount,
    List<ExpectedItemPrintRow> expectedItems,
    List<ReconciledItemPrintRow> reconciledItems) {
  public record ExpectedItemPrintRow(
      UUID itemId,
      String inventoryNumber,
      String qrCode,
      String itemName,
      UUID roomId,
      UUID scannedRoomId,
      UUID custodianEmployeeId,
      BigDecimal bookValue,
      String lineStatus,
      Instant scannedAt) {}

  public record ReconciledItemPrintRow(
      UUID itemId,
      String inventoryNumber,
      String qrCode,
      String itemName,
      UUID expectedRoomId,
      UUID scannedRoomId,
      String categoryCode,
      String categoryLabel,
      boolean transitionedToQidiruvda,
      Instant scannedAt) {}
}
