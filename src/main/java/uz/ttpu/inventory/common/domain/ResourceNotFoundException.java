package uz.ttpu.inventory.common.domain;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String resourceName, UUID id) {
    super(resourceName + " not found with id: " + id);
  }

  public ResourceNotFoundException(String message) {
    super(message);
  }
}
