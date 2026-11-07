package uz.ttpu.inventory.integration.api;

import jakarta.validation.constraints.NotBlank;

public record HemisEmployeePayloadDto(
    @NotBlank String hemisId,
    @NotBlank String employeeCode,
    @NotBlank String firstName,
    @NotBlank String lastName,
    String middleName,
    String email,
    String phone,
    String position,
    String departmentCode) {}
