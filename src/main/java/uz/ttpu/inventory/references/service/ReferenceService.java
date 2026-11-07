package uz.ttpu.inventory.references.service;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
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
import uz.ttpu.inventory.references.api.ReferenceRevisionDto;
import uz.ttpu.inventory.references.api.ReferenceSyncApi;
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

@Service
@RequiredArgsConstructor
public class ReferenceService implements ReferenceSyncApi {

  private final BuildingRepository buildingRepository;
  private final FloorRepository floorRepository;
  private final DepartmentRepository departmentRepository;
  private final RoomRepository roomRepository;
  private final EmployeeRepository employeeRepository;
  private final CategoryRepository categoryRepository;
  private final ReferenceMapper referenceMapper;
  private final EntityManager entityManager;

  // =========================================================================
  // BUILDINGS
  // =========================================================================

  @Transactional(readOnly = true)
  public List<BuildingDto> listBuildings(boolean includeInactive) {
    List<Building> buildings =
        includeInactive ? buildingRepository.findAll() : buildingRepository.findAllByActiveTrue();
    return buildings.stream().map(referenceMapper::toBuildingDto).toList();
  }

  @Transactional(readOnly = true)
  public BuildingDto getBuilding(UUID id) {
    return referenceMapper.toBuildingDto(findBuildingOrThrow(id));
  }

  @Transactional
  public BuildingDto createBuilding(BuildingRequest request) {
    Building building =
        Building.builder()
            .code(request.code())
            .name(request.name())
            .address(request.address())
            .description(request.description())
            .build();
    building.setActive(true);
    return referenceMapper.toBuildingDto(buildingRepository.save(building));
  }

  @Transactional
  public BuildingDto updateBuilding(UUID id, BuildingRequest request) {
    Building building = findBuildingOrThrow(id);
    building.setCode(request.code());
    building.setName(request.name());
    building.setAddress(request.address());
    building.setDescription(request.description());
    return referenceMapper.toBuildingDto(buildingRepository.save(building));
  }

  @Transactional
  public void deleteBuilding(UUID id) {
    Building building = findBuildingOrThrow(id);
    building.setActive(false);
    buildingRepository.save(building);
  }

  // =========================================================================
  // FLOORS
  // =========================================================================

  @Transactional(readOnly = true)
  public List<FloorDto> listFloors(UUID buildingId, boolean includeInactive) {
    List<Floor> floors;
    if (buildingId != null) {
      floors =
          includeInactive
              ? floorRepository.findAll().stream()
                  .filter(f -> f.getBuilding().getId().equals(buildingId))
                  .toList()
              : floorRepository.findAllByBuildingIdAndActiveTrue(buildingId);
    } else {
      floors =
          includeInactive
              ? floorRepository.findAll()
              : floorRepository.findAll().stream().filter(Floor::isActive).toList();
    }
    return floors.stream().map(referenceMapper::toFloorDto).toList();
  }

  @Transactional(readOnly = true)
  public FloorDto getFloor(UUID id) {
    return referenceMapper.toFloorDto(findFloorOrThrow(id));
  }

  @Transactional
  public FloorDto createFloor(FloorRequest request) {
    Building building = findBuildingOrThrow(request.buildingId());
    Floor floor =
        Floor.builder()
            .building(building)
            .floorNumber(request.floorNumber())
            .name(request.name())
            .build();
    floor.setActive(true);
    return referenceMapper.toFloorDto(floorRepository.save(floor));
  }

  @Transactional
  public FloorDto updateFloor(UUID id, FloorRequest request) {
    Floor floor = findFloorOrThrow(id);
    Building building = findBuildingOrThrow(request.buildingId());
    floor.setBuilding(building);
    floor.setFloorNumber(request.floorNumber());
    floor.setName(request.name());
    return referenceMapper.toFloorDto(floorRepository.save(floor));
  }

  @Transactional
  public void deleteFloor(UUID id) {
    Floor floor = findFloorOrThrow(id);
    floor.setActive(false);
    floorRepository.save(floor);
  }

  // =========================================================================
  // DEPARTMENTS
  // =========================================================================

  @Transactional(readOnly = true)
  public List<DepartmentDto> listDepartments(boolean includeInactive) {
    List<Department> departments =
        includeInactive
            ? departmentRepository.findAll()
            : departmentRepository.findAllByActiveTrue();
    return departments.stream().map(referenceMapper::toDepartmentDto).toList();
  }

  @Transactional(readOnly = true)
  public DepartmentDto getDepartment(UUID id) {
    return referenceMapper.toDepartmentDto(findDepartmentOrThrow(id));
  }

  @Transactional
  public DepartmentDto createDepartment(DepartmentRequest request) {
    Department parent =
        request.parentId() != null ? findDepartmentOrThrow(request.parentId()) : null;
    Building building =
        request.buildingId() != null ? findBuildingOrThrow(request.buildingId()) : null;
    Department department =
        Department.builder()
            .code(request.code())
            .name(request.name())
            .parent(parent)
            .building(building)
            .build();
    department.setActive(true);
    return referenceMapper.toDepartmentDto(departmentRepository.save(department));
  }

  @Transactional
  public DepartmentDto updateDepartment(UUID id, DepartmentRequest request) {
    Department department = findDepartmentOrThrow(id);
    Department parent =
        request.parentId() != null ? findDepartmentOrThrow(request.parentId()) : null;
    Building building =
        request.buildingId() != null ? findBuildingOrThrow(request.buildingId()) : null;
    department.setCode(request.code());
    department.setName(request.name());
    department.setParent(parent);
    department.setBuilding(building);
    return referenceMapper.toDepartmentDto(departmentRepository.save(department));
  }

  @Transactional
  public void deleteDepartment(UUID id) {
    Department department = findDepartmentOrThrow(id);
    department.setActive(false);
    departmentRepository.save(department);
  }

  // =========================================================================
  // ROOMS
  // =========================================================================

  @Transactional(readOnly = true)
  public List<RoomDto> listRooms(UUID floorId, UUID departmentId, boolean includeInactive) {
    List<Room> rooms;
    if (floorId != null && !includeInactive) {
      rooms = roomRepository.findAllByFloorIdAndActiveTrue(floorId);
    } else if (departmentId != null && !includeInactive) {
      rooms = roomRepository.findAllByDepartmentIdAndActiveTrue(departmentId);
    } else {
      rooms =
          roomRepository.findAll().stream()
              .filter(r -> includeInactive || r.isActive())
              .filter(r -> floorId == null || r.getFloor().getId().equals(floorId))
              .filter(
                  r ->
                      departmentId == null
                          || (r.getDepartment() != null
                              && r.getDepartment().getId().equals(departmentId)))
              .toList();
    }
    return rooms.stream().map(referenceMapper::toRoomDto).toList();
  }

  @Transactional(readOnly = true)
  public RoomDto getRoom(UUID id) {
    return referenceMapper.toRoomDto(findRoomOrThrow(id));
  }

  @Transactional
  public RoomDto createRoom(RoomRequest request) {
    Floor floor = findFloorOrThrow(request.floorId());
    Department department =
        request.departmentId() != null ? findDepartmentOrThrow(request.departmentId()) : null;
    Room room =
        Room.builder()
            .floor(floor)
            .department(department)
            .roomNumber(request.roomNumber())
            .name(request.name())
            .capacity(request.capacity())
            .build();
    room.setActive(true);
    return referenceMapper.toRoomDto(roomRepository.save(room));
  }

  @Transactional
  public RoomDto updateRoom(UUID id, RoomRequest request) {
    Room room = findRoomOrThrow(id);
    Floor floor = findFloorOrThrow(request.floorId());
    Department department =
        request.departmentId() != null ? findDepartmentOrThrow(request.departmentId()) : null;
    room.setFloor(floor);
    room.setDepartment(department);
    room.setRoomNumber(request.roomNumber());
    room.setName(request.name());
    room.setCapacity(request.capacity());
    return referenceMapper.toRoomDto(roomRepository.save(room));
  }

  @Transactional
  public void deleteRoom(UUID id) {
    Room room = findRoomOrThrow(id);
    room.setActive(false);
    roomRepository.save(room);
  }

  // =========================================================================
  // EMPLOYEES
  // =========================================================================

  @Transactional(readOnly = true)
  public List<EmployeeDto> listEmployees(UUID departmentId, boolean includeInactive) {
    List<Employee> employees;
    if (departmentId != null && !includeInactive) {
      employees = employeeRepository.findAllByDepartmentIdAndActiveTrue(departmentId);
    } else {
      employees =
          employeeRepository.findAll().stream()
              .filter(e -> includeInactive || e.isActive())
              .filter(
                  e ->
                      departmentId == null
                          || (e.getDepartment() != null
                              && e.getDepartment().getId().equals(departmentId)))
              .toList();
    }
    return employees.stream().map(referenceMapper::toEmployeeDto).toList();
  }

  @Transactional(readOnly = true)
  public EmployeeDto getEmployee(UUID id) {
    return referenceMapper.toEmployeeDto(findEmployeeOrThrow(id));
  }

  @Transactional
  public EmployeeDto createEmployee(EmployeeRequest request) {
    Department department =
        request.departmentId() != null ? findDepartmentOrThrow(request.departmentId()) : null;
    Room room = request.roomId() != null ? findRoomOrThrow(request.roomId()) : null;
    Employee employee =
        Employee.builder()
            .employeeCode(request.employeeCode())
            .firstName(request.firstName())
            .lastName(request.lastName())
            .middleName(request.middleName())
            .email(request.email())
            .phone(request.phone())
            .position(request.position())
            .department(department)
            .room(room)
            .hemisId(request.hemisId())
            .oneCId(request.oneCId())
            .build();
    employee.setActive(true);
    return referenceMapper.toEmployeeDto(employeeRepository.save(employee));
  }

  @Transactional
  public EmployeeDto updateEmployee(UUID id, EmployeeRequest request) {
    Employee employee = findEmployeeOrThrow(id);
    Department department =
        request.departmentId() != null ? findDepartmentOrThrow(request.departmentId()) : null;
    Room room = request.roomId() != null ? findRoomOrThrow(request.roomId()) : null;
    employee.setEmployeeCode(request.employeeCode());
    employee.setFirstName(request.firstName());
    employee.setLastName(request.lastName());
    employee.setMiddleName(request.middleName());
    employee.setEmail(request.email());
    employee.setPhone(request.phone());
    employee.setPosition(request.position());
    employee.setDepartment(department);
    employee.setRoom(room);
    employee.setHemisId(request.hemisId());
    employee.setOneCId(request.oneCId());
    return referenceMapper.toEmployeeDto(employeeRepository.save(employee));
  }

  @Transactional
  public void deleteEmployee(UUID id) {
    Employee employee = findEmployeeOrThrow(id);
    employee.setActive(false);
    employeeRepository.save(employee);
  }

  // =========================================================================
  // CATEGORIES
  // =========================================================================

  @Transactional(readOnly = true)
  public List<CategoryDto> listCategories(boolean includeInactive) {
    List<Category> categories =
        includeInactive ? categoryRepository.findAll() : categoryRepository.findAllByActiveTrue();
    return categories.stream().map(referenceMapper::toCategoryDto).toList();
  }

  @Transactional(readOnly = true)
  public CategoryDto getCategory(UUID id) {
    return referenceMapper.toCategoryDto(findCategoryOrThrow(id));
  }

  @Transactional
  public CategoryDto createCategory(CategoryRequest request) {
    Category parent = request.parentId() != null ? findCategoryOrThrow(request.parentId()) : null;
    Category category =
        Category.builder()
            .code(request.code())
            .name(request.name())
            .parent(parent)
            .depreciationYears(request.depreciationYears())
            .customFields(
                request.customFields() != null
                    ? new HashMap<>(request.customFields())
                    : new HashMap<>())
            .build();
    category.setActive(true);
    return referenceMapper.toCategoryDto(categoryRepository.save(category));
  }

  @Transactional
  public CategoryDto updateCategory(UUID id, CategoryRequest request) {
    Category category = findCategoryOrThrow(id);
    Category parent = request.parentId() != null ? findCategoryOrThrow(request.parentId()) : null;
    category.setCode(request.code());
    category.setName(request.name());
    category.setParent(parent);
    category.setDepreciationYears(request.depreciationYears());
    category.setCustomFields(
        request.customFields() != null ? new HashMap<>(request.customFields()) : new HashMap<>());
    return referenceMapper.toCategoryDto(categoryRepository.save(category));
  }

  @Transactional
  public void deleteCategory(UUID id) {
    Category category = findCategoryOrThrow(id);
    category.setActive(false);
    categoryRepository.save(category);
  }

  // =========================================================================
  // HIBERNATE ENVERS REVISION HISTORY
  // =========================================================================

  @Transactional(readOnly = true)
  public List<ReferenceRevisionDto<BuildingDto>> getBuildingRevisions(UUID id) {
    return queryRevisions(Building.class, id, referenceMapper::toBuildingDto);
  }

  @Transactional(readOnly = true)
  public List<ReferenceRevisionDto<FloorDto>> getFloorRevisions(UUID id) {
    return queryRevisions(Floor.class, id, referenceMapper::toFloorDto);
  }

  @Transactional(readOnly = true)
  public List<ReferenceRevisionDto<DepartmentDto>> getDepartmentRevisions(UUID id) {
    return queryRevisions(Department.class, id, referenceMapper::toDepartmentDto);
  }

  @Transactional(readOnly = true)
  public List<ReferenceRevisionDto<RoomDto>> getRoomRevisions(UUID id) {
    return queryRevisions(Room.class, id, referenceMapper::toRoomDto);
  }

  @Transactional(readOnly = true)
  public List<ReferenceRevisionDto<EmployeeDto>> getEmployeeRevisions(UUID id) {
    return queryRevisions(Employee.class, id, referenceMapper::toEmployeeDto);
  }

  @Transactional(readOnly = true)
  public List<ReferenceRevisionDto<CategoryDto>> getCategoryRevisions(UUID id) {
    return queryRevisions(Category.class, id, referenceMapper::toCategoryDto);
  }

  @SuppressWarnings("unchecked")
  private <E, D> List<ReferenceRevisionDto<D>> queryRevisions(
      Class<E> entityClass, UUID id, Function<E, D> mapper) {
    AuditReader auditReader = AuditReaderFactory.get(entityManager);
    List<Object[]> results =
        auditReader
            .createQuery()
            .forRevisionsOfEntity(entityClass, false, true)
            .add(AuditEntity.id().eq(id))
            .getResultList();

    List<ReferenceRevisionDto<D>> revisions = new ArrayList<>(results.size());
    for (Object[] row : results) {
      E entitySnapshot = (E) row[0];
      Number revNumber = extractRevisionNumber(auditReader, entityClass, id, row[1]);
      Instant revTimestamp = auditReader.getRevisionDate(revNumber).toInstant();
      RevisionType revType = (RevisionType) row[2];
      revisions.add(
          new ReferenceRevisionDto<>(
              revNumber.longValue(), revTimestamp, revType.name(), mapper.apply(entitySnapshot)));
    }
    return revisions;
  }

  private Number extractRevisionNumber(
      AuditReader auditReader, Class<?> entityClass, UUID id, Object revEntity) {
    try {
      var getIdMethod = revEntity.getClass().getMethod("getId");
      Object val = getIdMethod.invoke(revEntity);
      if (val instanceof Number num) {
        return num;
      }
    } catch (ReflectiveOperationException ignored) {
      // Fallback to first revision number
    }
    List<Number> nums = auditReader.getRevisions(entityClass, id);
    return nums.isEmpty() ? 1L : nums.get(nums.size() - 1);
  }

  @Override
  @Transactional
  public DepartmentDto upsertDepartmentFromHemis(String code, String name) {
    Department department =
        departmentRepository
            .findByCodeIgnoreCase(code)
            .orElseGet(
                () -> {
                  Department created = Department.builder().code(code).name(name).build();
                  created.setActive(true);
                  return created;
                });
    department.setName(name);
    department.setActive(true);
    return referenceMapper.toDepartmentDto(departmentRepository.save(department));
  }

  @Override
  @Transactional
  public EmployeeDto upsertEmployeeFromHemis(
      String hemisId,
      String employeeCode,
      String firstName,
      String lastName,
      String middleName,
      String email,
      String phone,
      String position,
      String departmentCode) {
    Department department =
        (departmentCode != null && !departmentCode.isBlank())
            ? departmentRepository.findByCodeIgnoreCase(departmentCode).orElse(null)
            : null;

    Employee employee =
        (hemisId != null && !hemisId.isBlank())
            ? employeeRepository
                .findByHemisId(hemisId)
                .or(() -> employeeRepository.findByEmployeeCodeIgnoreCase(employeeCode))
                .orElseGet(Employee::new)
            : employeeRepository
                .findByEmployeeCodeIgnoreCase(employeeCode)
                .orElseGet(Employee::new);

    employee.setHemisId(hemisId);
    employee.setEmployeeCode(employeeCode);
    employee.setFirstName(firstName);
    employee.setLastName(lastName);
    employee.setMiddleName(middleName);
    employee.setEmail(email);
    employee.setPhone(phone);
    employee.setPosition(position);
    if (department != null) {
      employee.setDepartment(department);
    }
    employee.setActive(true);
    return referenceMapper.toEmployeeDto(employeeRepository.save(employee));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<BuildingDto> findBuildingByCode(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    return buildingRepository.findByCodeIgnoreCase(code.trim()).map(referenceMapper::toBuildingDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<FloorDto> findFloorByBuildingAndNumber(UUID buildingId, int floorNumber) {
    if (buildingId == null) {
      return Optional.empty();
    }
    return floorRepository.findAllByBuildingIdAndActiveTrue(buildingId).stream()
        .filter(f -> f.getFloorNumber() != null && f.getFloorNumber() == floorNumber)
        .findFirst()
        .map(referenceMapper::toFloorDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DepartmentDto> findDepartmentByCode(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    return departmentRepository
        .findByCodeIgnoreCase(code.trim())
        .map(referenceMapper::toDepartmentDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RoomDto> findRoomByBuildingAndRoomNumber(UUID buildingId, String roomNumber) {
    if (roomNumber == null || roomNumber.isBlank()) {
      return Optional.empty();
    }
    String normalizedRoom = roomNumber.trim();
    return roomRepository.findAll().stream()
        .filter(Room::isActive)
        .filter(
            r ->
                buildingId == null
                    || (r.getFloor() != null
                        && r.getFloor().getBuilding() != null
                        && buildingId.equals(r.getFloor().getBuilding().getId())))
        .filter(r -> normalizedRoom.equalsIgnoreCase(r.getRoomNumber()))
        .findFirst()
        .map(referenceMapper::toRoomDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<EmployeeDto> findEmployeeByCodeOrFullName(String employeeCode, String fullName) {
    if (employeeCode != null && !employeeCode.isBlank()) {
      Optional<Employee> byCode =
          employeeRepository.findByEmployeeCodeIgnoreCase(employeeCode.trim());
      if (byCode.isPresent()) {
        return byCode.map(referenceMapper::toEmployeeDto);
      }
    }
    if (fullName != null && !fullName.isBlank()) {
      String target = fullName.trim().toLowerCase(Locale.ROOT);
      return employeeRepository.findAll().stream()
          .filter(Employee::isActive)
          .filter(
              e -> {
                String fl =
                    (e.getFirstName() + " " + e.getLastName()).trim().toLowerCase(Locale.ROOT);
                String lf =
                    (e.getLastName() + " " + e.getFirstName()).trim().toLowerCase(Locale.ROOT);
                String flm =
                    (e.getFirstName()
                            + " "
                            + e.getLastName()
                            + (e.getMiddleName() != null ? " " + e.getMiddleName() : ""))
                        .trim()
                        .toLowerCase(Locale.ROOT);
                return target.equals(fl)
                    || target.equals(lf)
                    || target.equals(flm)
                    || flm.contains(target)
                    || target.contains(fl);
              })
          .findFirst()
          .map(referenceMapper::toEmployeeDto);
    }
    return Optional.empty();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CategoryDto> findCategoryByCode(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    return categoryRepository.findByCodeIgnoreCase(code.trim()).map(referenceMapper::toCategoryDto);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CategoryDto> findCategoryById(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return categoryRepository.findById(id).map(referenceMapper::toCategoryDto);
  }

  @Override
  @Transactional
  public CategoryDto ensurePilotCategory(String code, String name, Integer depreciationYears) {
    return categoryRepository
        .findByCodeIgnoreCase(code.trim())
        .map(referenceMapper::toCategoryDto)
        .orElseGet(
            () ->
                createCategory(
                    new CategoryRequest(
                        code.trim(),
                        name != null && !name.isBlank() ? name.trim() : code.trim(),
                        null,
                        depreciationYears != null ? depreciationYears : 5,
                        HashMap.newHashMap(0))));
  }

  private Building findBuildingOrThrow(UUID id) {
    return buildingRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Building", id));
  }

  private Floor findFloorOrThrow(UUID id) {
    return floorRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Floor", id));
  }

  private Department findDepartmentOrThrow(UUID id) {
    return departmentRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Department", id));
  }

  private Room findRoomOrThrow(UUID id) {
    return roomRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Room", id));
  }

  private Employee findEmployeeOrThrow(UUID id) {
    return employeeRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
  }

  private Category findCategoryOrThrow(UUID id) {
    return categoryRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Category", id));
  }
}
