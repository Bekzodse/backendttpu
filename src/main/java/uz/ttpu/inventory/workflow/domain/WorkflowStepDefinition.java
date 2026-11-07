package uz.ttpu.inventory.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
@Table(
    name = "workflow_step_definitions",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_workflow_step_def_order",
          columnNames = {"definition_id", "step_order"})
    })
public class WorkflowStepDefinition extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "definition_id", nullable = false)
  private WorkflowDefinition definition;

  @Column(name = "step_order", nullable = false)
  private int stepOrder;

  @Column(name = "step_name", nullable = false, length = 150)
  private String stepName;

  @Column(name = "approver_role", nullable = false, length = 64)
  private String approverRole;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "required_verification_method", nullable = false, length = 32)
  private VerificationMethod requiredVerificationMethod = VerificationMethod.EITHER;

  @Column(name = "min_amount_condition", precision = 18, scale = 2)
  private BigDecimal minAmountCondition;

  @Column(name = "max_amount_condition", precision = 18, scale = 2)
  private BigDecimal maxAmountCondition;

  @Builder.Default
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "condition_attributes", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> conditionAttributes = new HashMap<>();

  public boolean matchesConditions(
      BigDecimal documentAmount, Map<String, Object> contextAttributes) {
    BigDecimal effectiveAmount = documentAmount != null ? documentAmount : BigDecimal.ZERO;
    if (minAmountCondition != null && effectiveAmount.compareTo(minAmountCondition) < 0) {
      return false;
    }
    if (maxAmountCondition != null && effectiveAmount.compareTo(maxAmountCondition) > 0) {
      return false;
    }
    if (conditionAttributes != null && !conditionAttributes.isEmpty()) {
      if (contextAttributes == null) {
        return false;
      }
      for (Map.Entry<String, Object> entry : conditionAttributes.entrySet()) {
        Object actualValue = contextAttributes.get(entry.getKey());
        if (!Objects.equals(String.valueOf(entry.getValue()), String.valueOf(actualValue))) {
          return false;
        }
      }
    }
    return true;
  }
}
