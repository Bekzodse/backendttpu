package uz.ttpu.inventory.references.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record EmployeeRequest(
    @NotBlank @Size(max = 50) String employeeCode,
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @Size(max = 100) String middleName,
    @Email @Size(max = 255) String email,
    @Size(max = 50) String phone,
    @Size(max = 150) String position,
    UUID departmentId,
    UUID roomId,
    @Size(max = 100) String hemisId,
    @Size(max = 100) String oneCId) {}
