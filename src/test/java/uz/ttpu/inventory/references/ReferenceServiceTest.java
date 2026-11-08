package uz.ttpu.inventory.references;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import uz.ttpu.inventory.references.api.BuildingDto;
import uz.ttpu.inventory.references.api.BuildingRequest;
import uz.ttpu.inventory.references.api.CategoryDto;
import uz.ttpu.inventory.references.api.CategoryRequest;
import uz.ttpu.inventory.references.api.DepartmentDto;
import uz.ttpu.inventory.references.api.DepartmentRequest;
import uz.ttpu.inventory.references.api.EmployeeDto;
import uz.ttpu.inventory.references.api.EmployeeRequest;
import uz.ttpu.inventory.references.api.FloorDto;
import uz.ttpu.inventory.references.api.FloorRequest;
import uz.ttpu.inventory.references.api.RoomDto;
import uz.ttpu.inventory.references.api.RoomRequest;
import uz.ttpu.inventory.references.domain.Building;
import uz.ttpu.inventory.references.domain.Category;
import uz.ttpu.inventory.references.domain.Department;
import uz.ttpu.inventory.references.domain.Employee;
import uz.ttpu.inventory.references.domain.Floor;
import uz.ttpu.inventory.references.domain.Room;
import uz.ttpu.inventory.references.repository.BuildingRepository;
import uz.ttpu.inventory.references.repository.CategoryRepository;
import uz.ttpu.inventory.references.repository.DepartmentRepository;
import uz.ttpu.inventory.references.repository.EmployeeRepository;
import uz.ttpu.inventory.references.repository.FloorRepository;
import uz.ttpu.inventory.references.repository.RoomRepository;
import uz.ttpu.inventory.references.service.ReferenceMapper;
import uz.ttpu.inventory.references.service.ReferenceService;

class ReferenceServiceTest {

  private BuildingRepository buildingRepository;
  private FloorRepository floorRepository;
  private DepartmentRepository departmentRepository;
  private RoomRepository roomRepository;
  private EmployeeRepository employeeRepository;
  private CategoryRepository categoryRepository;
  private ReferenceService service;

  @BeforeEach
  void setUp() {
    buildingRepository = mock(BuildingRepository.class);
    floorRepository = mock(FloorRepository.class);
    departmentRepository = mock(DepartmentRepository.class);
    roomRepository = mock(RoomRepository.class);
    employeeRepository = mock(EmployeeRepository.class);
    categoryRepository = mock(CategoryRepository.class);
    ReferenceMapper mapper = Mappers.getMapper(ReferenceMapper.class);
    EntityManager entityManager = mock(EntityManager.class);

    service =
        new ReferenceService(
            buildingRepository,
            floorRepository,
            departmentRepository,
            roomRepository,
            employeeRepository,
            categoryRepository,
            mapper,
            entityManager);
  }

  @Test
  void deletingAnyReferenceEntityPerformsSoftDeactivation() {
    UUID id = UUID.randomUUID();

    Building building = Building.builder().code("B-MAIN").name("Main Academic Building").build();
    building.setId(id);
    when(buildingRepository.findById(id)).thenReturn(Optional.of(building));
    service.deleteBuilding(id);
    assertThat(building.isActive()).isFalse();
    verify(buildingRepository).save(building);

    Floor floor = Floor.builder().building(building).floorNumber(1).name("1st Floor").build();
    floor.setId(id);
    when(floorRepository.findById(id)).thenReturn(Optional.of(floor));
    service.deleteFloor(id);
    assertThat(floor.isActive()).isFalse();
    verify(floorRepository).save(floor);

    Department department = Department.builder().code("DEP-IT").name("IT Department").build();
    department.setId(id);
    when(departmentRepository.findById(id)).thenReturn(Optional.of(department));
    service.deleteDepartment(id);
    assertThat(department.isActive()).isFalse();
    verify(departmentRepository).save(department);

    Room room = Room.builder().floor(floor).roomNumber("101").name("Server Room").build();
    room.setId(id);
    when(roomRepository.findById(id)).thenReturn(Optional.of(room));
    service.deleteRoom(id);
    assertThat(room.isActive()).isFalse();
    verify(roomRepository).save(room);

    Employee employee =
        Employee.builder().employeeCode("EMP-001").firstName("Ali").lastName("Valiyev").build();
    employee.setId(id);
    when(employeeRepository.findById(id)).thenReturn(Optional.of(employee));
    service.deleteEmployee(id);
    assertThat(employee.isActive()).isFalse();
    verify(employeeRepository).save(employee);

    Category category = Category.builder().code("CAT-LAPTOP").name("Laptops").build();
    category.setId(id);
    when(categoryRepository.findById(id)).thenReturn(Optional.of(category));
    service.deleteCategory(id);
    assertThat(category.isActive()).isFalse();
    verify(categoryRepository).save(category);
  }

  @Test
  void createCategoryMapsCustomFieldsJsonbToDtoRecord() {
    when(categoryRepository.save(any(Category.class)))
        .thenAnswer(
            inv -> {
              Category saved = inv.getArgument(0);
              saved.setId(UUID.randomUUID());
              return saved;
            });

    Map<String, Object> customSchema =
        Map.of(
            "cpu", "string",
            "ramGb", "integer",
            "hasGpu", true);
    CategoryRequest request = new CategoryRequest("CAT-PC", "Workstations", null, 5, customSchema);

    CategoryDto dto = service.createCategory(request);

    assertThat(dto.id()).isNotNull();
    assertThat(dto.code()).isEqualTo("CAT-PC");
    assertThat(dto.active()).isTrue();
    assertThat(dto.customFields()).containsEntry("cpu", "string").containsEntry("ramGb", "integer");
  }

  @Test
  void testBuildingCrudAndListing() {
    UUID bldId = UUID.randomUUID();
    Building building =
        Building.builder().code("B-01").name("Campus Alpha").address("Little Ring Road").build();
    building.setId(bldId);
    building.setActive(true);

    when(buildingRepository.save(any(Building.class)))
        .thenAnswer(
            i -> {
              Building b = i.getArgument(0);
              if (b.getId() == null) b.setId(bldId);
              return b;
            });
    when(buildingRepository.findById(bldId)).thenReturn(Optional.of(building));
    when(buildingRepository.findAll()).thenReturn(java.util.List.of(building));
    when(buildingRepository.findAllByActiveTrue()).thenReturn(java.util.List.of(building));

    BuildingDto created =
        service.createBuilding(
            new BuildingRequest("B-01", "Campus Alpha", "Little Ring Road", "Desc"));
    assertThat(created.code()).isEqualTo("B-01");

    BuildingDto fetched = service.getBuilding(bldId);
    assertThat(fetched.name()).isEqualTo("Campus Alpha");

    BuildingDto updated =
        service.updateBuilding(
            bldId, new BuildingRequest("B-01-U", "Campus Alpha Updated", "New Addr", "New Desc"));
    assertThat(updated.name()).isEqualTo("Campus Alpha Updated");

    java.util.List<BuildingDto> list = service.listBuildings(false);
    assertThat(list).hasSize(1);
    assertThat(service.listBuildings(true)).hasSize(1);
  }

  @Test
  void testFloorCrudAndListing() {
    UUID bldId = UUID.randomUUID();
    UUID flrId = UUID.randomUUID();
    Building building = Building.builder().code("B-01").name("Main Building").build();
    building.setId(bldId);
    Floor floor = Floor.builder().building(building).floorNumber(2).name("Second Floor").build();
    floor.setId(flrId);
    floor.setActive(true);

    when(buildingRepository.findById(bldId)).thenReturn(Optional.of(building));
    when(floorRepository.save(any(Floor.class)))
        .thenAnswer(
            i -> {
              Floor f = i.getArgument(0);
              if (f.getId() == null) f.setId(flrId);
              return f;
            });
    when(floorRepository.findById(flrId)).thenReturn(Optional.of(floor));
    when(floorRepository.findAllByBuildingIdAndActiveTrue(bldId))
        .thenReturn(java.util.List.of(floor));
    when(floorRepository.findAll()).thenReturn(java.util.List.of(floor));

    FloorDto created = service.createFloor(new FloorRequest(bldId, 2, "Second Floor"));
    assertThat(created.floorNumber()).isEqualTo(2);

    FloorDto fetched = service.getFloor(flrId);
    assertThat(fetched.name()).isEqualTo("Second Floor");

    FloorDto updated = service.updateFloor(flrId, new FloorRequest(bldId, 3, "Third Floor"));
    assertThat(updated.floorNumber()).isEqualTo(3);

    assertThat(service.listFloors(bldId, false)).hasSize(1);
    assertThat(service.listFloors(null, true)).hasSize(1);
  }

  @Test
  void testDepartmentCrudAndListing() {
    UUID depId = UUID.randomUUID();
    Department dep = Department.builder().code("DEP-FIN").name("Finance").build();
    dep.setId(depId);
    dep.setActive(true);

    when(departmentRepository.save(any(Department.class)))
        .thenAnswer(
            i -> {
              Department d = i.getArgument(0);
              if (d.getId() == null) d.setId(depId);
              return d;
            });
    when(departmentRepository.findById(depId)).thenReturn(Optional.of(dep));
    when(departmentRepository.findAll()).thenReturn(java.util.List.of(dep));
    when(departmentRepository.findAllByActiveTrue()).thenReturn(java.util.List.of(dep));

    DepartmentDto created =
        service.createDepartment(new DepartmentRequest("DEP-FIN", "Finance", null, null));
    assertThat(created.code()).isEqualTo("DEP-FIN");

    DepartmentDto fetched = service.getDepartment(depId);
    assertThat(fetched.name()).isEqualTo("Finance");

    DepartmentDto updated =
        service.updateDepartment(
            depId, new DepartmentRequest("DEP-FIN-MOD", "Finance Modified", null, null));
    assertThat(updated.name()).isEqualTo("Finance Modified");

    assertThat(service.listDepartments(false)).hasSize(1);
    assertThat(service.listDepartments(true)).hasSize(1);
  }

  @Test
  void testRoomCrudAndListing() {
    UUID flrId = UUID.randomUUID();
    UUID roomId = UUID.randomUUID();
    Floor floor = Floor.builder().floorNumber(1).name("1F").build();
    floor.setId(flrId);
    Room room = Room.builder().floor(floor).roomNumber("204").name("CAD Lab").capacity(30).build();
    room.setId(roomId);
    room.setActive(true);

    when(floorRepository.findById(flrId)).thenReturn(Optional.of(floor));
    when(roomRepository.save(any(Room.class)))
        .thenAnswer(
            i -> {
              Room r = i.getArgument(0);
              if (r.getId() == null) r.setId(roomId);
              return r;
            });
    when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
    when(roomRepository.findAllByFloorIdAndActiveTrue(flrId)).thenReturn(java.util.List.of(room));
    when(roomRepository.findAll()).thenReturn(java.util.List.of(room));

    RoomDto created = service.createRoom(new RoomRequest(flrId, null, "204", "CAD Lab", 30));
    assertThat(created.roomNumber()).isEqualTo("204");

    RoomDto fetched = service.getRoom(roomId);
    assertThat(fetched.name()).isEqualTo("CAD Lab");

    RoomDto updated =
        service.updateRoom(roomId, new RoomRequest(flrId, null, "205", "CAD Lab 2", 35));
    assertThat(updated.roomNumber()).isEqualTo("205");

    assertThat(service.listRooms(flrId, null, false)).hasSize(1);
  }

  @Test
  void testEmployeeCrudAndListing() {
    UUID empId = UUID.randomUUID();
    Employee emp =
        Employee.builder()
            .employeeCode("EMP-88")
            .firstName("Nodira")
            .lastName("Begim")
            .email("n.begim@ttpu.uz")
            .build();
    emp.setId(empId);
    emp.setActive(true);

    when(employeeRepository.save(any(Employee.class)))
        .thenAnswer(
            i -> {
              Employee e = i.getArgument(0);
              if (e.getId() == null) e.setId(empId);
              return e;
            });
    when(employeeRepository.findById(empId)).thenReturn(Optional.of(emp));
    when(employeeRepository.findAll()).thenReturn(java.util.List.of(emp));

    EmployeeDto created =
        service.createEmployee(
            new EmployeeRequest(
                "EMP-88",
                "Nodira",
                "Begim",
                null,
                "n.begim@ttpu.uz",
                null,
                "Auditor",
                null,
                null,
                null,
                null));
    assertThat(created.firstName()).isEqualTo("Nodira");

    EmployeeDto fetched = service.getEmployee(empId);
    assertThat(fetched.lastName()).isEqualTo("Begim");

    EmployeeDto updated =
        service.updateEmployee(
            empId,
            new EmployeeRequest(
                "EMP-88",
                "Nodira",
                "Begim Updated",
                null,
                "n.begim@ttpu.uz",
                null,
                "Senior Auditor",
                null,
                null,
                null,
                null));
    assertThat(updated.lastName()).isEqualTo("Begim Updated");

    assertThat(service.listEmployees(null, true)).hasSize(1);
  }

  @Test
  void testCategoryCrudAndListing() {
    UUID catId = UUID.randomUUID();
    Category cat = Category.builder().code("CAT-MON").name("Monitors").depreciationYears(5).build();
    cat.setId(catId);
    cat.setActive(true);

    when(categoryRepository.findById(catId)).thenReturn(Optional.of(cat));
    when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));
    when(categoryRepository.findAll()).thenReturn(java.util.List.of(cat));
    when(categoryRepository.findAllByActiveTrue()).thenReturn(java.util.List.of(cat));

    CategoryDto fetched = service.getCategory(catId);
    assertThat(fetched.name()).isEqualTo("Monitors");

    CategoryDto updated =
        service.updateCategory(
            catId, new CategoryRequest("CAT-MON", "LED Monitors", null, 6, Map.of()));
    assertThat(updated.name()).isEqualTo("LED Monitors");

    assertThat(service.listCategories(false)).hasSize(1);
    assertThat(service.listCategories(true)).hasSize(1);
  }

  @Test
  void testHemisUpsertsAndFindMethods() {
    Department dep = Department.builder().code("HEM-DEP").name("Hemis Department").build();
    dep.setId(UUID.randomUUID());
    dep.setActive(true);

    Employee emp =
        Employee.builder().employeeCode("HEM-EMP").firstName("Hemis").lastName("User").build();
    emp.setId(UUID.randomUUID());
    emp.setActive(true);

    when(departmentRepository.findByCodeIgnoreCase("HEM-DEP")).thenReturn(Optional.of(dep));
    when(employeeRepository.findByEmployeeCodeIgnoreCase("HEM-EMP")).thenReturn(Optional.of(emp));
    when(departmentRepository.save(any(Department.class))).thenAnswer(i -> i.getArgument(0));
    when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));

    DepartmentDto hemisDep =
        service.upsertDepartmentFromHemis("HEM-DEP", "Hemis Department Updated");
    assertThat(hemisDep).isNotNull();

    EmployeeDto hemisEmp =
        service.upsertEmployeeFromHemis(
            "HEM-123",
            "HEM-EMP",
            "Hemis",
            "User",
            null,
            "hemis@ttpu.uz",
            null,
            "Developer",
            "HEM-DEP");
    assertThat(hemisEmp).isNotNull();

    assertThat(service.findDepartmentByCode("HEM-DEP")).isPresent();
    assertThat(service.findEmployeeByCodeOrFullName("HEM-EMP", null)).isPresent();
  }
}
