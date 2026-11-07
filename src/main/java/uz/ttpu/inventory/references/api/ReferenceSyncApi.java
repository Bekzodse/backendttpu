package uz.ttpu.inventory.references.api;

public interface ReferenceSyncApi {

    DepartmentDto upsertDepartmentFromHemis(String code, String name);

    EmployeeDto upsertEmployeeFromHemis(
            String hemisId,
            String employeeCode,
            String firstName,
            String lastName,
            String middleName,
            String email,
            String phone,
            String position,
            String departmentCode
    );
}
