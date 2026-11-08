package uz.ttpu.inventory.inventory.api;

import java.util.List;
import java.util.UUID;

public record OfflineSyncBatchResponseDto(
    UUID sessionId,
    long packageVersion,
    int totalSubmitted,
    int appliedCount,
    int duplicateIgnoredCount,
    int conflictCount,
    List<OfflineScanActionResultDto> results) {

  public OfflineSyncBatchResponseDto(
      UUID sessionId,
      long packageVersion,
      int totalSubmitted,
      int appliedCount,
      int duplicateIgnoredCount,
      List<OfflineScanActionResultDto> results) {
    this(
        sessionId,
        packageVersion,
        totalSubmitted,
        appliedCount,
        duplicateIgnoredCount,
        results != null
            ? (int) results.stream().filter(OfflineScanActionResultDto::conflict).count()
            : 0,
        results);
  }
}
