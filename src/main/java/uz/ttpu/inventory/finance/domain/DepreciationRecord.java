package uz.ttpu.inventory.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "depreciation_records")
public class DepreciationRecord extends BaseEntity {

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "inventory_number", nullable = false, length = 100)
    private String inventoryNumber;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Column(name = "initial_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal initialValue;

    @Builder.Default
    @Column(name = "salvage_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal salvageValue = BigDecimal.ZERO;

    @Column(name = "useful_life_years", nullable = false)
    private int usefulLifeYears;

    @Column(name = "depreciable_base", nullable = false, precision = 18, scale = 2)
    private BigDecimal depreciableBase;

    @Column(name = "annual_depreciation", nullable = false, precision = 18, scale = 2)
    private BigDecimal annualDepreciation;

    @Column(name = "monthly_depreciation", nullable = false, precision = 18, scale = 2)
    private BigDecimal monthlyDepreciation;

    @Builder.Default
    @Column(name = "accumulated_depreciation", nullable = false, precision = 18, scale = 2)
    private BigDecimal accumulatedDepreciation = BigDecimal.ZERO;

    @Column(name = "net_book_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal netBookValue;

    @Column(name = "calculated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant calculatedAt;
}
