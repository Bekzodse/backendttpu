package uz.ttpu.inventory.inventory.api;

import java.util.Optional;
import java.util.UUID;

public interface InventoryLockApi {

  boolean isRoomLockedForInventory(UUID roomId);

  Optional<String> findActiveSessionCodeForRoom(UUID roomId);

  RoomLockStatusDto getRoomLockStatus(UUID roomId);
}
