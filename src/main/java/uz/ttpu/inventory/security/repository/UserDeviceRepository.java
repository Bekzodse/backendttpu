package uz.ttpu.inventory.security.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.security.domain.UserDevice;

public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {
  Optional<UserDevice> findByUserIdAndDeviceId(UUID userId, String deviceId);

  Optional<UserDevice> findByDeviceId(String deviceId);

  List<UserDevice> findAllByUserIdOrderByLastLoginAtDesc(UUID userId);

  List<UserDevice> findAllByRevokedFalse();

  List<UserDevice> findAllByRevokedTrue();
}
