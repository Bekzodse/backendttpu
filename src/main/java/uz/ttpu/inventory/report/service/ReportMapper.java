package uz.ttpu.inventory.report.service;

import org.mapstruct.Mapper;
import uz.ttpu.inventory.report.api.ReportExportLogDto;
import uz.ttpu.inventory.report.domain.ReportExportLog;

@Mapper
public interface ReportMapper {

  ReportExportLogDto toDto(ReportExportLog entity);
}
