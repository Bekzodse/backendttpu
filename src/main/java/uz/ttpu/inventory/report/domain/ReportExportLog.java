package uz.ttpu.inventory.report.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
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
@Table(name = "report_export_logs")
public class ReportExportLog extends BaseEntity {

  @Column(name = "report_code", nullable = false, length = 100)
  private String reportCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "report_type", nullable = false, length = 64)
  private ReportType reportType;

  @Column(name = "row_count", nullable = false)
  private int rowCount;

  @Column(name = "file_size_bytes", nullable = false)
  private long fileSizeBytes;

  @Column(name = "generation_time_ms", nullable = false)
  private long generationTimeMs;

  @Column(name = "generated_by_username", nullable = false, length = 150)
  private String generatedByUsername;

  @Column(name = "generated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private Instant generatedAt;
}
