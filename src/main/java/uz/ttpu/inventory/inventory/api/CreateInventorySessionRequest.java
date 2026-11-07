package uz.ttpu.inventory.inventory.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateInventorySessionRequest(
        @NotBlank String sessionCode,
        @NotBlank String rectorOrderNumber,
        @NotNull Instant rectorOrderDate,
        @NotNull Instant sessionDate,
        @NotNull UUID chairmanEmployeeId,
        @NotEmpty List<UUID> commissionMemberIds,
        @NotEmpty List<UUID> roomIds,
        String notes,
        boolean generateExpectedListImmediately
) {
}
