package uz.ttpu.inventory.workflow.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.ttpu.inventory.common.domain.BaseEntity;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "workflow_instances")
public class WorkflowInstance extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "definition_id", nullable = false)
  private WorkflowDefinition definition;

  @Enumerated(EnumType.STRING)
  @Column(name = "document_type", nullable = false, length = 50)
  private WorkflowDocumentType documentType;

  @Column(name = "document_id", nullable = false)
  private UUID documentId;

  @Column(name = "document_number", length = 100)
  private String documentNumber;

  @Builder.Default
  @Column(name = "amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal amount = BigDecimal.ZERO;

  @Builder.Default
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "context_attributes", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> contextAttributes = new HashMap<>();

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false, length = 50)
  private WorkflowState state = WorkflowState.DRAFT;

  @Column(name = "current_step_order")
  private Integer currentStepOrder;

  @Column(name = "initiated_by_user_id")
  private UUID initiatedByUserId;

  @Column(name = "submitted_at", columnDefinition = "TIMESTAMPTZ")
  private Instant submittedAt;

  @Column(name = "completed_at", columnDefinition = "TIMESTAMPTZ")
  private Instant completedAt;

  @Column(name = "rejection_reason", columnDefinition = "TEXT")
  private String rejectionReason;

  @Column(name = "rework_reason", columnDefinition = "TEXT")
  private String reworkReason;

  @Builder.Default
  @OneToMany(mappedBy = "instance", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("decidedAt ASC")
  private List<WorkflowStepDecision> decisions = new ArrayList<>();

  public void addDecision(WorkflowStepDecision decision) {
    this.decisions.add(decision);
    decision.setInstance(this);
  }
}
