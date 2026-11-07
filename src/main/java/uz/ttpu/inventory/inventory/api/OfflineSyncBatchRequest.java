package uz.ttpu.inventory.inventory.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record OfflineSyncBatchRequest(
    UUID sessionId, @NotEmpty List<@Valid OfflineScanActionRequest> actions) {}
