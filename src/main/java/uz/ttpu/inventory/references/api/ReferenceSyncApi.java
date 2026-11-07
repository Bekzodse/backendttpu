package uz.ttpu.inventory.references.api;

import java.util.Optional;
import java.util.UUID;

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
      String departmentCode);

  Optional<BuildingDto> findBuildingByCode(String code);

  Optional<FloorDto> findFloorByBuildingAndNumber(UUID buildingId, int floorNumber);

  Optional<DepartmentDto> findDepartmentByCode(String code);

  Optional<RoomDto> findRoomByBuildingAndRoomNumber(UUID buildingId, String roomNumber);

  Optional<EmployeeDto> findEmployeeByCodeOrFullName(String employeeCode, String fullName);

  Optional<CategoryDto> findCategoryByCode(String code);

  Optional<CategoryDto> findCategoryById(UUID id);

  CategoryDto ensurePilotCategory(String code, String name, Integer depreciationYears);
}
