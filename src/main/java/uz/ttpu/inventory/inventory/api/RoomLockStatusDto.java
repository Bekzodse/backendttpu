package uz.ttpu.inventory.inventory.api;

import java.util.UUID;

public record RoomLockStatusDto(
    UUID roomId, boolean locked, String sessionCode, UUID sessionId, String rectorOrderNumber) {}
