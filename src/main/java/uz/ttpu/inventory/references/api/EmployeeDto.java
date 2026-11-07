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
    Instant updatedAt) {}
