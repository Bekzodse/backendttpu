package uz.ttpu.inventory.common.domain;

import java.util.UUID;
import lombok.Getter;

@Getter
public class RoomLockedForInventoryException extends RuntimeException {

  private final UUID roomId;
  private final String sessionCode;

  public RoomLockedForInventoryException(UUID roomId, String sessionCode) {
    super(
        String.format(
            "Room '%s' is locked due to active inventory session '%s'. Asset transfers into or out"
                + " of this room are prohibited until inventory is completed.",
            roomId, sessionCode != null ? sessionCode : "IN_PROGRESS"));
    this.roomId = roomId;
    this.sessionCode = sessionCode;
  }
}
