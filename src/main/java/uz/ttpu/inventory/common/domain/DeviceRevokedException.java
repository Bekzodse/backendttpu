package uz.ttpu.inventory.common.domain;

import lombok.Getter;

@Getter
public class DeviceRevokedException extends RuntimeException {

  private final String deviceId;

  public DeviceRevokedException(String deviceId) {
    super(
        String.format(
            "Device '%s' has been revoked by an administrator. Access is denied.", deviceId));
    this.deviceId = deviceId;
  }
}
