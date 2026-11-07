package uz.ttpu.inventory.repair.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RepairReplacementRecommendedEvent(
    UUID ticketId,
    String ticketNumber,
    UUID itemId,
    BigDecimal repairCost,
    BigDecimal residualValue,
    String reason,
    Instant flaggedAt) {}
