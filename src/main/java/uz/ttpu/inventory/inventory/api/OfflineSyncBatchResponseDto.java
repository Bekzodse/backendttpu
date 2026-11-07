package uz.ttpu.inventory.inventory.api;

import java.util.List;
import java.util.UUID;

public record OfflineSyncBatchResponseDto(
    UUID sessionId,
    long packageVersion,
    int totalSubmitted,
    int appliedCount,
    int duplicateIgnoredCount,
    List<OfflineScanActionResultDto> results) {}
