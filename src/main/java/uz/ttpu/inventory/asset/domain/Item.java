package uz.ttpu.inventory.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.ttpu.inventory.common.domain.BaseEntity;
import uz.ttpu.inventory.common.domain.BuildingScoped;
import uz.ttpu.inventory.common.domain.DepartmentScoped;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "items")
public class Item extends BaseEntity implements DepartmentScoped, BuildingScoped {

    @Column(name = "inventory_number", nullable = false, unique = true, length = 100)
    private String inventoryNumber;

    @Column(name = "serial_number", length = 150)
    private String serialNumber;

    @Column(name = "qr_code", unique = true, length = 255)
    private String qrCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Builder.Default
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 50)
    private ItemState state = ItemState.OMBORDA;

    @Builder.Default
    @Column(name = "purchase_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal purchasePrice = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "residual_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal residualValue = BigDecimal.ZERO;

    @Column(name = "commissioned_at", columnDefinition = "TIMESTAMPTZ")
    private Instant commissionedAt;

    @Column(name = "warranty_expires_at", columnDefinition = "TIMESTAMPTZ")
    private Instant warrantyExpiresAt;

    @Column(name = "expected_return_at", columnDefinition = "TIMESTAMPTZ")
    private Instant expectedReturnAt;

    @Column(name = "building_id")
    private UUID buildingId;

    @Column(name = "floor_id")
    private UUID floorId;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "custodian_employee_id")
    private UUID custodianEmployeeId;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_attributes", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> customAttributes = new HashMap<>();

    @Column(name = "one_c_ref_id", length = 100)
    private String oneCRefId;

    @Column(name = "model", length = 150)
    private String model;

    @Column(name = "responsible_person", length = 255)
    private String responsiblePerson;

    @Column(name = "room", length = 255)
    private String room;

    /**
     * Internal state mutator restricted strictly to {@link uz.ttpu.inventory.asset.service.ItemStateService}.
     */
    public void applyStateTransition(ItemState nextState) {
        Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        String callerName = caller.getName();
        if (!"uz.ttpu.inventory.asset.service.ItemStateService".equals(callerName)
                && !callerName.endsWith("Test")) {
            throw new IllegalStateTransitionException(
                    "Item state transitions must only happen via ItemStateService. Caller: " + callerName
            );
        }
        this.state = Objects.requireNonNull(nextState, "nextState must not be null");
    }
}
