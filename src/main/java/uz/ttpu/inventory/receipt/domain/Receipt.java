package uz.ttpu.inventory.receipt.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
@Table(name = "receipts")
public class Receipt extends BaseEntity {

    @Column(name = "receipt_number", nullable = false, unique = true, length = 100)
    private String receiptNumber;

    @Column(name = "contract_number", nullable = false, length = 150)
    private String contractNumber;

    @Column(name = "contract_date", columnDefinition = "TIMESTAMPTZ")
    private Instant contractDate;

    @Column(name = "invoice_number", nullable = false, length = 150)
    private String invoiceNumber;

    @Column(name = "invoice_date", columnDefinition = "TIMESTAMPTZ")
    private Instant invoiceDate;

    @Column(name = "supplier_name", nullable = false, length = 255)
    private String supplierName;

    @Column(name = "supplier_tin", length = 50)
    private String supplierTin;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ReceiptStatus status = ReceiptStatus.DRAFT;

    @Column(name = "received_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant receivedAt;

    @Column(name = "warehouse_building_id")
    private UUID warehouseBuildingId;

    @Column(name = "received_by_employee_id")
    private UUID receivedByEmployeeId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Builder.Default
    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReceiptLine> lines = new ArrayList<>();

    public void addLine(ReceiptLine line) {
        this.lines.add(line);
        line.setReceipt(this);
    }

    public void removeLine(ReceiptLine line) {
        this.lines.remove(line);
        line.setReceipt(null);
    }
}
