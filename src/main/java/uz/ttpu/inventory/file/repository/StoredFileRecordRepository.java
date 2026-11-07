package uz.ttpu.inventory.file.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.file.domain.StoredFileRecord;

public interface StoredFileRecordRepository extends JpaRepository<StoredFileRecord, UUID> {

    Optional<StoredFileRecord> findByFileKey(String fileKey);

    List<StoredFileRecord> findAllByReferenceEntityIdOrderByUploadedAtDesc(UUID referenceEntityId);
}
