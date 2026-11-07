package uz.ttpu.inventory.repair.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.repair.domain.RepairTicket;
import uz.ttpu.inventory.repair.domain.RepairTicketState;

public interface RepairTicketRepository extends JpaRepository<RepairTicket, UUID> {
  List<RepairTicket> findAllByItemIdOrderByCreatedAtDesc(UUID itemId);

  List<RepairTicket> findAllByStateInAndExpectedReturnAtBefore(
      Collection<RepairTicketState> states, Instant now);

  List<RepairTicket> findAllByReplacementRecommendedTrue();
}
