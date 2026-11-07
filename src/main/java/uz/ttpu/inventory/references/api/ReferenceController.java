package uz.ttpu.inventory.references.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.references.service.ReferenceService;

@RestController
@RequestMapping("/api/v1/references")
@RequiredArgsConstructor
@Tag(
        name = "02. Reference Data & Envers History",
        description = "CRUD operations, soft-deactivation (active=false), and Hibernate Envers revision history for Buildings, Floors, Departments, Rooms, Employees, and Asset Categories (with JSONB custom fields)."
)
public class ReferenceController {

    private final ReferenceService referenceService;

    // --- Buildings ---

    @GetMapping("/buildings")
    @Operation(summary = "List university buildings", description = "Returns all active buildings, or all buildings including deactivated ones when includeInactive=true.")
    public ResponseEntity<List<BuildingDto>> listBuildings(
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return ResponseEntity.ok(referenceService.listBuildings(includeInactive));
    }

    @GetMapping("/buildings/{id}")
    @Operation(summary = "Get building by ID", description = "Retrieves a single university building reference record by its UUID.")
    public ResponseEntity<BuildingDto> getBuilding(@PathVariable UUID id) {
        return ResponseEntity.ok(referenceService.getBuilding(id));
    }

    @PostMapping("/buildings")
    @Operation(summary = "Create a new building", description = "Creates a new university building and records an initial ADD revision in Hibernate Envers.")
    public ResponseEntity<BuildingDto> createBuilding(@Valid @RequestBody BuildingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(referenceService.createBuilding(request));
    }

    @PutMapping("/buildings/{id}")
    @Operation(summary = "Update an existing building", description = "Updates building details and records a MOD revision in Hibernate Envers.")
    public ResponseEntity<BuildingDto> updateBuilding(
            @PathVariable UUID id,
            @Valid @RequestBody BuildingRequest request
    ) {
        return ResponseEntity.ok(referenceService.updateBuilding(id, request));
    }

    @DeleteMapping("/buildings/{id}")
    @Operation(summary = "Soft-delete a building", description = "Sets active=false on the building record without physical deletion and logs the change in Hibernate Envers.")
    public ResponseEntity<Void> deleteBuilding(@PathVariable UUID id) {
        referenceService.deleteBuilding(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/buildings/{id}/revisions")
    @Operation(summary = "Get Hibernate Envers change history for a building", description = "Returns all historical audit revisions (ADD, MOD) for the specified building.")
    public ResponseEntity<List<ReferenceRevisionDto<BuildingDto>>> getBuildingRevisions(@PathVariable UUID id) {
        return ResponseEntity.ok(referenceService.getBuildingRevisions(id));
    }

    // --- Floors ---

    @GetMapping("/floors")
    @Operation(summary = "List building floors", description = "Returns floors optionally filtered by buildingId and active status.")
    public ResponseEntity<List<FloorDto>> listFloors(
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return ResponseEntity.ok(referenceService.listFloors(buildingId, includeInactive));
    }

    @GetMapping("/floors/{id}")
    @Operation(summary = "Get floor by ID", description = "Retrieves a single floor reference record by its UUID.")
    public ResponseEntity<FloorDto> getFloor(@PathVariable UUID id) {
        return ResponseEntity.ok(referenceService.getFloor(id));
    }

    @PostMapping("/floors")
    @Operation(summary = "Create a new floor", description = "Creates a floor within a building and records an Envers audit revision.")
    public ResponseEntity<FloorDto> createFloor(@Valid @RequestBody FloorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(referenceService.createFloor(request));
    }

    @PutMapping("/floors/{id}")
    @Operation(summary = "Update an existing floor", description = "Updates floor number or name and records an Envers audit revision.")
    public ResponseEntity<FloorDto> updateFloor(
            @PathVariable UUID id,
            @Valid @RequestBody FloorRequest request
    ) {
        return ResponseEntity.ok(referenceService.updateFloor(id, request));
    }

    @DeleteMapping("/floors/{id}")
    @Operation(summary = "Soft-delete a floor", description = "Marks the floor as inactive (active=false) without hard-deleting the row.")
    public ResponseEntity<Void> deleteFloor(@PathVariable UUID id) {
        referenceService.deleteFloor(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/floors/{id}/revisions")
    @Operation(summary = "Get Hibernate Envers change history for a floor", description = "Returns all historical audit revisions for the specified floor.")
    public ResponseEntity<List<ReferenceRevisionDto<FloorDto>>> getFloorRevisions(@PathVariable UUID id) {
        return ResponseEntity.ok(referenceService.getFloorRevisions(id));
    }

    // --- Departments ---

    @GetMapping("/departments")
    @Operation(summary = "List university departments", description = "Returns all active departments, or includes inactive departments when includeInactive=true.")
    public ResponseEntity<List<DepartmentDto>> listDepartments(
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return ResponseEntity.ok(referenceService.listDepartments(includeInactive));
    }

    @GetMapping("/departments/{id}")
    @Operation(summary = "Get department by ID", description = "Retrieves a single department reference record by its UUID.")
    public ResponseEntity<DepartmentDto> getDepartment(@PathVariable UUID id) {
        return ResponseEntity.ok(referenceService.getDepartment(id));
    }

    @PostMapping("/departments")
    @Operation(summary = "Create a new department", description = "Creates a university department/faculty and records an Envers audit revision.")
    public ResponseEntity<DepartmentDto> createDepartment(@Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(referenceService.createDepartment(request));
    }

    @PutMapping("/departments/{id}")
    @Operation(summary = "Update an existing department", description = "Updates department metadata, parent hierarchy, or building assignment.")
    public ResponseEntity<DepartmentDto> updateDepartment(
            @PathVariable UUID id,
            @Valid @RequestBody DepartmentRequest request
    ) {
        return ResponseEntity.ok(referenceService.updateDepartment(id, request));
    }

    @DeleteMapping("/departments/{id}")
    @Operation(summary = "Soft-delete a department", description = "Sets active=false on the department without removing historical foreign keys.")
    public ResponseEntity<Void> deleteDepartment(@PathVariable UUID id) {
        referenceService.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/departments/{id}/revisions")
    @Operation(summary = "Get Hibernate Envers change history for a department", description = "Returns all historical audit revisions for the specified department.")
    public ResponseEntity<List<ReferenceRevisionDto<DepartmentDto>>> getDepartmentRevisions(@PathVariable UUID id) {
        return ResponseEntity.ok(referenceService.getDepartmentRevisions(id));
    }

    // --- Rooms ---

    @GetMapping("/rooms")
    @Operation(summary = "List university rooms and laboratories", description = "Returns rooms optionally filtered by floorId, departmentId, and active status.")
    public ResponseEntity<List<RoomDto>> listRooms(
            @RequestParam(required = false) UUID floorId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return ResponseEntity.ok(referenceService.listRooms(floorId, departmentId, includeInactive));
    }

    @GetMapping("/rooms/{id}")
    @Operation(summary = "Get room by ID", description = "Retrieves a single room or laboratory reference record by its UUID.")
    public ResponseEntity<RoomDto> getRoom(@PathVariable UUID id) {
        return ResponseEntity.ok(referenceService.getRoom(id));
    }

    @PostMapping("/rooms")
    @Operation(summary = "Create a new room", description = "Creates a room assigned to a floor and optional department, recording an Envers revision.")
    public ResponseEntity<RoomDto> createRoom(@Valid @RequestBody RoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(referenceService.createRoom(request));
    }

    @PutMapping("/rooms/{id}")
    @Operation(summary = "Update an existing room", description = "Updates room number, name, capacity, or department assignment.")
    public ResponseEntity<RoomDto> updateRoom(
            @PathVariable UUID id,
            @Valid @RequestBody RoomRequest request
    ) {
        return ResponseEntity.ok(referenceService.updateRoom(id, request));
    }

    @DeleteMapping("/rooms/{id}")
    @Operation(summary = "Soft-delete a room", description = "Sets active=false on the room record without physical deletion.")
    public ResponseEntity<Void> deleteRoom(@PathVariable UUID id) {
        referenceService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/rooms/{id}/revisions")
    @Operation(summary = "Get Hibernate Envers change history for a room", description = "Returns all historical audit revisions for the specified room.")
    public ResponseEntity<List<ReferenceRevisionDto<RoomDto>>> getRoomRevisions(@PathVariable UUID id) {
        return ResponseEntity.ok(referenceService.getRoomRevisions(id));
    }

    // --- Employees ---

    @GetMapping("/employees")
    @Operation(summary = "List university employees and custodians", description = "Returns employees optionally filtered by departmentId and active flag.")
    public ResponseEntity<List<EmployeeDto>> listEmployees(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return ResponseEntity.ok(referenceService.listEmployees(departmentId, includeInactive));
    }

    @GetMapping("/employees/{id}")
    @Operation(summary = "Get employee by ID", description = "Retrieves a single employee record by its UUID.")
    public ResponseEntity<EmployeeDto> getEmployee(@PathVariable UUID id) {
        return ResponseEntity.ok(referenceService.getEmployee(id));
    }

    @PostMapping("/employees")
    @Operation(summary = "Create a new employee", description = "Creates an employee record with HEMIS and 1C reference IDs and records an Envers revision.")
    public ResponseEntity<EmployeeDto> createEmployee(@Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(referenceService.createEmployee(request));
    }

    @PutMapping("/employees/{id}")
    @Operation(summary = "Update an existing employee", description = "Updates employee contact details, position, department, or room assignment.")
    public ResponseEntity<EmployeeDto> updateEmployee(
            @PathVariable UUID id,
            @Valid @RequestBody EmployeeRequest request
    ) {
        return ResponseEntity.ok(referenceService.updateEmployee(id, request));
    }

    @DeleteMapping("/employees/{id}")
    @Operation(summary = "Soft-delete an employee", description = "Sets active=false on the employee record without physical deletion.")
    public ResponseEntity<Void> deleteEmployee(@PathVariable UUID id) {
        referenceService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/employees/{id}/revisions")
    @Operation(summary = "Get Hibernate Envers change history for an employee", description = "Returns all historical audit revisions for the specified employee.")
    public ResponseEntity<List<ReferenceRevisionDto<EmployeeDto>>> getEmployeeRevisions(@PathVariable UUID id) {
        return ResponseEntity.ok(referenceService.getEmployeeRevisions(id));
    }

    // --- Categories ---

    @GetMapping("/categories")
    @Operation(summary = "List asset categories", description = "Returns asset categories including JSONB custom field schemas and depreciation useful life years.")
    public ResponseEntity<List<CategoryDto>> listCategories(
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return ResponseEntity.ok(referenceService.listCategories(includeInactive));
    }

    @GetMapping("/categories/{id}")
    @Operation(summary = "Get asset category by ID", description = "Retrieves a single asset category and its JSONB customFields definition.")
    public ResponseEntity<CategoryDto> getCategory(@PathVariable UUID id) {
        return ResponseEntity.ok(referenceService.getCategory(id));
    }

    @PostMapping("/categories")
    @Operation(summary = "Create a new asset category", description = "Creates an asset category with useful life years and JSONB custom field schema.")
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(referenceService.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    @Operation(summary = "Update an existing asset category", description = "Updates category details, depreciation years, or JSONB customFields.")
    public ResponseEntity<CategoryDto> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request
    ) {
        return ResponseEntity.ok(referenceService.updateCategory(id, request));
    }

    @DeleteMapping("/categories/{id}")
    @Operation(summary = "Soft-delete an asset category", description = "Sets active=false on the category record without physical deletion.")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        referenceService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/categories/{id}/revisions")
    @Operation(summary = "Get Hibernate Envers change history for an asset category", description = "Returns all historical audit revisions for the specified category.")
    public ResponseEntity<List<ReferenceRevisionDto<CategoryDto>>> getCategoryRevisions(@PathVariable UUID id) {
        return ResponseEntity.ok(referenceService.getCategoryRevisions(id));
    }
}
