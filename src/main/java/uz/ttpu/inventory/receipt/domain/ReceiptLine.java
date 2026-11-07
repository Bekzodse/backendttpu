package uz.ttpu.inventory.receipt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
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
    name = "receipt_lines",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_receipt_lines_category_serial",
          columnNames = {"category_id", "serial_number"})
    })
public class ReceiptLine extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "receipt_id", nullable = false)
  private Receipt receipt;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @Column(name = "serial_number", nullable = false, length = 150)
  private String serialNumber;

  @Column(name = "price_without_vat", nullable = false, precision = 18, scale = 2)
  private BigDecimal priceWithoutVat;

  @Builder.Default
  @Column(name = "quantity", nullable = false)
  private Integer quantity = 1;
}
