package uz.ttpu.inventory.references.api;

import java.time.Instant;
import java.util.UUID;

public record EmployeeDto(
    UUID id,
    String employeeCode,
    String firstName,
    String lastName,
    String middleName,
    String email,
    String phone,
    String position,
    UUID departmentId,
    UUID roomId,
    String hemisId,
    String oneCId,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public String fullName() {
    StringBuilder sb = new StringBuilder();
    if (lastName != null && !lastName.isBlank()) {
      sb.append(lastName.trim());
    }
    if (firstName != null && !firstName.isBlank()) {
      if (!sb.isEmpty()) {
        sb.append(" ");
      }
      sb.append(firstName.trim());
    }
    if (middleName != null && !middleName.isBlank()) {
      if (!sb.isEmpty()) {
        sb.append(" ");
      }
      sb.append(middleName.trim());
    }
    return sb.toString();
  }
}
