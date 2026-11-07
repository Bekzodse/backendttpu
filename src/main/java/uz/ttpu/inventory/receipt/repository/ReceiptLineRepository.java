package uz.ttpu.inventory.receipt.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.receipt.domain.ReceiptLine;

public interface ReceiptLineRepository extends JpaRepository<ReceiptLine, UUID> {
  boolean existsByCategoryIdAndSerialNumberIgnoreCase(UUID categoryId, String serialNumber);

  List<ReceiptLine> findAllByReceiptId(UUID receiptId);
}
