package uz.ttpu.inventory.receipt.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import uz.ttpu.inventory.receipt.domain.Receipt;

public interface ReceiptRepository
    extends JpaRepository<Receipt, UUID>, JpaSpecificationExecutor<Receipt> {
  Optional<Receipt> findByReceiptNumberIgnoreCase(String receiptNumber);
}
