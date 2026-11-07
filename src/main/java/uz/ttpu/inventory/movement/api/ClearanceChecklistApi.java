package uz.ttpu.inventory.movement.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClearanceChecklistApi {

  ClearanceChecklistDto openChecklist(OpenClearanceChecklistRequest request);

  ClearanceChecklistDto closeChecklist(UUID checklistId, CloseClearanceChecklistRequest request);

  ClearanceChecklistDto getChecklist(UUID checklistId);

  Optional<ClearanceChecklistDto> getActiveChecklistForEmployee(UUID employeeId);

  List<ClearanceChecklistDto> listChecklists();
}
