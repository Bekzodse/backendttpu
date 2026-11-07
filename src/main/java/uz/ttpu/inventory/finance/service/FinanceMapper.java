package uz.ttpu.inventory.finance.service;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uz.ttpu.inventory.finance.api.DepreciationForecastDto;
import uz.ttpu.inventory.finance.api.YearlyDepreciationForecastDto;
import uz.ttpu.inventory.finance.domain.DepreciationRecord;

@Mapper
public interface FinanceMapper {

  @Mapping(target = "yearlySchedule", source = "yearlySchedule")
  DepreciationForecastDto toDto(
      DepreciationRecord entity, List<YearlyDepreciationForecastDto> yearlySchedule);
}
