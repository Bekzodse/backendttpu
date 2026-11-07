package uz.ttpu.inventory.references.service;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uz.ttpu.inventory.references.api.BuildingDto;
import uz.ttpu.inventory.references.api.CategoryDto;
import uz.ttpu.inventory.references.api.DepartmentDto;
import uz.ttpu.inventory.references.api.EmployeeDto;
import uz.ttpu.inventory.references.api.FloorDto;
import uz.ttpu.inventory.references.api.RoomDto;
import uz.ttpu.inventory.references.domain.Building;
import uz.ttpu.inventory.references.domain.Category;
import uz.ttpu.inventory.references.domain.Department;
import uz.ttpu.inventory.references.domain.Employee;
import uz.ttpu.inventory.references.domain.Floor;
import uz.ttpu.inventory.references.domain.Room;

@Mapper(componentModel = "spring")
public interface ReferenceMapper {

  BuildingDto toBuildingDto(Building building);

  @Mapping(target = "buildingId", source = "building.id")
  FloorDto toFloorDto(Floor floor);

  @Mapping(target = "parentId", source = "parent.id")
  @Mapping(target = "buildingId", source = "building.id")
  DepartmentDto toDepartmentDto(Department department);

  @Mapping(target = "floorId", source = "floor.id")
  @Mapping(target = "departmentId", source = "department.id")
  RoomDto toRoomDto(Room room);

  @Mapping(target = "departmentId", source = "department.id")
  @Mapping(target = "roomId", source = "room.id")
  EmployeeDto toEmployeeDto(Employee employee);

  @Mapping(target = "parentId", source = "parent.id")
  CategoryDto toCategoryDto(Category category);
}
