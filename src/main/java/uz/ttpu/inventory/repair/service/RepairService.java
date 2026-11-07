package uz.ttpu.inventory.repair.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.repair.api.CompleteRepairRequest;
import uz.ttpu.inventory.repair.api.CreateRepairTicketRequest;
import uz.ttpu.inventory.repair.api.ItemMovedToVendorRepairEvent;
import uz.ttpu.inventory.repair.api.ItemRepairCompletedEvent;
import uz.ttpu.inventory.repair.api.OverdueRepairTicketAlertEvent;
import uz.ttpu.inventory.repair.api.RepairTicketDto;
import uz.ttpu.inventory.repair.api.StartInProgressRepairRequest;
import uz.ttpu.inventory.repair.api.VendorRepairRequest;
import uz.ttpu.inventory.repair.domain.RepairTicket;
import uz.ttpu.inventory.repair.domain.RepairTicketState;
import uz.ttpu.inventory.repair.repository.RepairTicketRepository;

@Service
@RequiredArgsConstructor
public class RepairService {

    private final RepairTicketRepository repairTicketRepository;
    private final RepairMapper repairMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<RepairTicketDto> listTickets() {
        return repairTicketRepository.findAll().stream()
                .map(repairMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public RepairTicketDto getTicket(UUID id) {
        return repairMapper.toDto(findTicketOrThrow(id));
    }

    @Transactional
    public RepairTicketDto createTicket(CreateRepairTicketRequest request) {
        RepairTicket ticket = RepairTicket.builder()
                .ticketNumber(request.ticketNumber())
                .itemId(request.itemId())
                .reportedByEmployeeId(request.reportedByEmployeeId())
                .assignedTechnicianEmployeeId(request.assignedTechnicianEmployeeId())
                .state(RepairTicketState.NEW)
                .issueDescription(request.issueDescription())
                .expectedReturnAt(request.expectedReturnAt())
                .build();
        return repairMapper.toDto(repairTicketRepository.save(ticket));
    }

    @Transactional
    public RepairTicketDto startInProgress(UUID ticketId, StartInProgressRepairRequest request) {
        RepairTicket ticket = findTicketOrThrow(ticketId);
        if (ticket.getState() != RepairTicketState.NEW) {
            throw new IllegalStateException("Ticket must be in NEW state to move to IN_PROGRESS, but was " + ticket.getState());
        }
        ticket.setState(RepairTicketState.IN_PROGRESS);
        ticket.setAssignedTechnicianEmployeeId(request.assignedTechnicianEmployeeId());
        if (request.expectedReturnAt() != null) {
            ticket.setExpectedReturnAt(request.expectedReturnAt());
        }
        return repairMapper.toDto(repairTicketRepository.save(ticket));
    }

    @Transactional
    public RepairTicketDto sendToVendorRepair(UUID ticketId, VendorRepairRequest request) {
        RepairTicket ticket = findTicketOrThrow(ticketId);
        if (ticket.getState() != RepairTicketState.NEW && ticket.getState() != RepairTicketState.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Ticket must be in NEW or IN_PROGRESS state to move to vendor repair (IN_SERVICE), but was " + ticket.getState()
            );
        }

        Instant now = Instant.now();
        ticket.setState(RepairTicketState.IN_SERVICE);
        ticket.setVendorName(request.vendorName());
        ticket.setVendorContractNumber(request.vendorContractNumber());
        ticket.setEstimatedCost(request.estimatedCost() != null ? request.estimatedCost() : BigDecimal.ZERO);
        if (request.expectedReturnAt() != null) {
            ticket.setExpectedReturnAt(request.expectedReturnAt());
        }

        RepairTicket saved = repairTicketRepository.save(ticket);

        eventPublisher.publishEvent(new ItemMovedToVendorRepairEvent(
                saved.getId(),
                saved.getTicketNumber(),
                saved.getItemId(),
                saved.getVendorName(),
                saved.getVendorContractNumber(),
                saved.getEstimatedCost(),
                saved.getExpectedReturnAt(),
                now
        ));

        return repairMapper.toDto(saved);
    }

    @Transactional
    public RepairTicketDto completeRepair(UUID ticketId, CompleteRepairRequest request) {
        RepairTicket ticket = findTicketOrThrow(ticketId);
        if (ticket.getState() != RepairTicketState.IN_PROGRESS && ticket.getState() != RepairTicketState.IN_SERVICE) {
            throw new IllegalStateException(
                    "Ticket must be in IN_PROGRESS or IN_SERVICE state to mark as DONE, but was " + ticket.getState()
            );
        }

        boolean wasVendorRepair = ticket.getState() == RepairTicketState.IN_SERVICE;
        Instant now = Instant.now();
        ticket.setState(RepairTicketState.DONE);
        ticket.setActualCost(request.actualCost() != null ? request.actualCost() : BigDecimal.ZERO);
        ticket.setResolutionNotes(request.resolutionNotes());
        ticket.setCompletedAt(now);

        RepairTicket saved = repairTicketRepository.save(ticket);

        if (wasVendorRepair) {
            eventPublisher.publishEvent(new ItemRepairCompletedEvent(
                    saved.getId(),
                    saved.getTicketNumber(),
                    saved.getItemId(),
                    saved.getActualCost(),
                    saved.getResolutionNotes(),
                    now
            ));
        }

        return repairMapper.toDto(saved);
    }

    @Transactional
    public RepairTicketDto closeTicket(UUID ticketId) {
        RepairTicket ticket = findTicketOrThrow(ticketId);
        if (ticket.getState() != RepairTicketState.DONE) {
            throw new IllegalStateException("Ticket must be in DONE state to be CLOSED, but was " + ticket.getState());
        }
        ticket.setState(RepairTicketState.CLOSED);
        ticket.setClosedAt(Instant.now());
        return repairMapper.toDto(repairTicketRepository.save(ticket));
    }

    @Transactional(readOnly = true)
    public List<RepairTicketDto> findOverdueRepairTickets(Instant now) {
        return repairTicketRepository.findAllByStateInAndExpectedReturnAtBefore(
                        Set.of(RepairTicketState.IN_PROGRESS, RepairTicketState.IN_SERVICE),
                        now
                ).stream()
                .map(repairMapper::toDto)
                .toList();
    }

    @Scheduled(cron = "${app.jobs.overdue-repairs-cron:0 15 8 * * *}", zone = "UTC")
    @SchedulerLock(name = "checkOverdueRepairTicketsJob", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
    @Transactional(readOnly = true)
    public void runDailyOverdueRepairTicketsJob() {
        checkAndPublishOverdueRepairTickets();
    }

    @Transactional(readOnly = true)
    public int checkAndPublishOverdueRepairTickets() {
        Instant now = Instant.now();
        List<RepairTicket> overdueTickets = repairTicketRepository.findAllByStateInAndExpectedReturnAtBefore(
                Set.of(RepairTicketState.IN_PROGRESS, RepairTicketState.IN_SERVICE),
                now
        );
        for (RepairTicket ticket : overdueTickets) {
            eventPublisher.publishEvent(new OverdueRepairTicketAlertEvent(
                    ticket.getId(),
                    ticket.getTicketNumber(),
                    ticket.getItemId(),
                    ticket.getState(),
                    ticket.getVendorName(),
                    ticket.getExpectedReturnAt(),
                    now
            ));
        }
        return overdueTickets.size();
    }

    private RepairTicket findTicketOrThrow(UUID id) {
        return repairTicketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RepairTicket", id));
    }
}
