package uz.ttpu.inventory.inventory.domain;

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
@Table(
        name = "inventory_expected_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inventory_expected_items_session_item", columnNames = {"session_id", "item_id"})
        }
)
public class InventoryExpectedItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private InventorySession session;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "inventory_number", nullable = false, length = 100)
    private String inventoryNumber;

    @Column(name = "qr_code", nullable = false, length = 32)
    private String qrCode;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Column(name = "custodian_employee_id")
    private UUID custodianEmployeeId;

    @Builder.Default
    @Column(name = "book_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal bookValue = BigDecimal.ZERO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "line_status", nullable = false, length = 32)
    private InventoryLineStatus lineStatus = InventoryLineStatus.EXPECTED;

    @Column(name = "scanned_room_id")
    private UUID scannedRoomId;

    @Column(name = "scanned_by_employee_id")
    private UUID scannedByEmployeeId;

    @Column(name = "scanned_at", columnDefinition = "TIMESTAMPTZ")
    private Instant scannedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
