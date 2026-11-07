package uz.ttpu.inventory.repair.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.ttpu.inventory.common.domain.BaseEntity;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "repair_tickets")
public class RepairTicket extends BaseEntity {

    @Column(name = "ticket_number", nullable = false, unique = true, length = 100)
    private String ticketNumber;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "reported_by_employee_id", nullable = false)
    private UUID reportedByEmployeeId;

    @Column(name = "assigned_technician_employee_id")
    private UUID assignedTechnicianEmployeeId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private RepairTicketState state = RepairTicketState.NEW;

    @Column(name = "issue_description", nullable = false, columnDefinition = "TEXT")
    private String issueDescription;

    @Column(name = "vendor_name", length = 255)
    private String vendorName;

    @Column(name = "vendor_contract_number", length = 150)
    private String vendorContractNumber;

    @Builder.Default
    @Column(name = "estimated_cost", nullable = false, precision = 18, scale = 2)
    private BigDecimal estimatedCost = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "actual_cost", nullable = false, precision = 18, scale = 2)
    private BigDecimal actualCost = BigDecimal.ZERO;

    @Column(name = "expected_return_at", columnDefinition = "TIMESTAMPTZ")
    private Instant expectedReturnAt;

    @Column(name = "completed_at", columnDefinition = "TIMESTAMPTZ")
    private Instant completedAt;

    @Column(name = "closed_at", columnDefinition = "TIMESTAMPTZ")
    private Instant closedAt;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;
}
