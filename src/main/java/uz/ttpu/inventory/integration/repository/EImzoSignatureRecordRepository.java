package uz.ttpu.inventory.integration.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.integration.domain.EImzoSignatureRecord;

public interface EImzoSignatureRecordRepository extends JpaRepository<EImzoSignatureRecord, UUID> {

    List<EImzoSignatureRecord> findAllByDocumentIdOrderByVerifiedAtDesc(UUID documentId);
}
