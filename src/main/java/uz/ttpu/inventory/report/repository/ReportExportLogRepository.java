package uz.ttpu.inventory.report.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.report.domain.ReportExportLog;

public interface ReportExportLogRepository extends JpaRepository<ReportExportLog, UUID> {

  List<ReportExportLog> findAllByOrderByGeneratedAtDesc();
}
