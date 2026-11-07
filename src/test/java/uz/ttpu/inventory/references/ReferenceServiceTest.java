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
import uz.ttpu.inventory.references.api.CategoryDto;
import uz.ttpu.inventory.references.api.CategoryRequest;
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

        service = new ReferenceService(
                buildingRepository,
                floorRepository,
                departmentRepository,
                roomRepository,
                employeeRepository,
                categoryRepository,
                mapper,
                entityManager
        );
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

        Employee employee = Employee.builder().employeeCode("EMP-001").firstName("Ali").lastName("Valiyev").build();
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
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        Map<String, Object> customSchema = Map.of(
                "cpu", "string",
                "ramGb", "integer",
                "hasGpu", true
        );
        CategoryRequest request = new CategoryRequest("CAT-PC", "Workstations", null, 5, customSchema);

        CategoryDto dto = service.createCategory(request);

        assertThat(dto.id()).isNotNull();
        assertThat(dto.code()).isEqualTo("CAT-PC");
        assertThat(dto.active()).isTrue();
        assertThat(dto.customFields()).containsEntry("cpu", "string").containsEntry("ramGb", "integer");
    }
}
