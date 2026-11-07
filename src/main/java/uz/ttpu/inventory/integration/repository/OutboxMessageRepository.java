package uz.ttpu.inventory.integration.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.integration.domain.ExternalSystemType;
import uz.ttpu.inventory.integration.domain.OutboxMessage;
import uz.ttpu.inventory.integration.domain.OutboxStatus;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    Optional<OutboxMessage> findByIdempotencyKey(String idempotencyKey);

    List<OutboxMessage> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<OutboxStatus> statuses,
            Instant now
    );

    List<OutboxMessage> findAllByTargetSystemOrderByCreatedAtDesc(ExternalSystemType targetSystem);

    List<OutboxMessage> findAllByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
