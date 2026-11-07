package uz.ttpu.inventory.request.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.request.domain.AssetRequest;
import uz.ttpu.inventory.request.domain.AssetRequestStatus;

public interface AssetRequestRepository extends JpaRepository<AssetRequest, UUID> {

  Optional<AssetRequest> findByRequestNumber(String requestNumber);

  List<AssetRequest> findAllByDepartmentIdOrderByCreatedAtDesc(UUID departmentId);

  List<AssetRequest> findAllByStatusOrderByCreatedAtDesc(AssetRequestStatus status);
}
