package uz.ttpu.inventory.notification.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.notification.domain.NotificationChannel;
import uz.ttpu.inventory.notification.domain.NotificationLog;
import uz.ttpu.inventory.notification.domain.NotificationStatus;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    List<NotificationLog> findAllByReferenceIdOrderByCreatedAtDesc(UUID referenceId);

    List<NotificationLog> findAllByChannelAndStatus(NotificationChannel channel, NotificationStatus status);
}
