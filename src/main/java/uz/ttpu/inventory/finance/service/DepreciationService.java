package uz.ttpu.inventory.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.finance.api.DepreciationCalculationRequest;
import uz.ttpu.inventory.finance.api.DepreciationForecastDto;
import uz.ttpu.inventory.finance.api.YearlyDepreciationForecastDto;
import uz.ttpu.inventory.finance.domain.DepreciationRecord;
import uz.ttpu.inventory.finance.repository.DepreciationRecordRepository;

@Service
@RequiredArgsConstructor
public class DepreciationService {

  private final DepreciationRecordRepository depreciationRecordRepository;
  private final AssetLogisticsApi assetLogisticsApi;
  private final FinanceMapper financeMapper;

  @Transactional
  public DepreciationForecastDto calculateAndSaveForecast(DepreciationCalculationRequest request) {
    if (request.usefulLifeYears() <= 0) {
      throw new IllegalArgumentException("usefulLifeYears must be greater than 0");
    }

    BigDecimal initialValue = request.initialValue().setScale(2, RoundingMode.HALF_UP);
    BigDecimal salvageValue =
        request.salvageValue() != null
            ? request.salvageValue().setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    if (salvageValue.compareTo(initialValue) > 0) {
      throw new IllegalArgumentException("salvageValue cannot exceed initialValue");
    }

    int usefulLife = request.usefulLifeYears();
    // Straight-line depreciation formula: (initial value - salvage value) / useful life
    BigDecimal depreciableBase =
        initialValue.subtract(salvageValue).setScale(2, RoundingMode.HALF_UP);
    BigDecimal annualDepreciation =
        depreciableBase.divide(BigDecimal.valueOf(usefulLife), 2, RoundingMode.HALF_UP);
    BigDecimal monthlyDepreciation =
        annualDepreciation.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

    List<YearlyDepreciationForecastDto> schedule =
        buildYearlySchedule(initialValue, salvageValue, usefulLife, annualDepreciation);

    int clampedElapsedYears = Math.min(Math.max(0, request.elapsedYears()), usefulLife);
    BigDecimal currentAccumulated =
        clampedElapsedYears == 0
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : schedule.get(clampedElapsedYears - 1).accumulatedDepreciation();
    BigDecimal currentNetBookValue =
        initialValue.subtract(currentAccumulated).setScale(2, RoundingMode.HALF_UP);

    Instant now = Instant.now();
    DepreciationRecord entity =
        DepreciationRecord.builder()
            .itemId(request.itemId())
            .inventoryNumber(request.inventoryNumber())
            .itemName(request.itemName())
            .initialValue(initialValue)
            .salvageValue(salvageValue)
            .usefulLifeYears(usefulLife)
            .depreciableBase(depreciableBase)
            .annualDepreciation(annualDepreciation)
            .monthlyDepreciation(monthlyDepreciation)
            .accumulatedDepreciation(currentAccumulated)
            .netBookValue(currentNetBookValue)
            .calculatedAt(now)
            .build();

    DepreciationRecord saved = depreciationRecordRepository.saveAndFlush(entity);
    return toForecastDto(saved, schedule);
  }

  @Transactional
  public DepreciationForecastDto calculateForecastForItem(
      UUID itemId, BigDecimal salvageValue, int usefulLifeYears, int elapsedYears) {
    ItemDto item = assetLogisticsApi.resolveItemByIdOrQr(itemId, null);
    return calculateAndSaveForecast(
        new DepreciationCalculationRequest(
            item.id(),
            item.inventoryNumber(),
            item.name(),
            item.purchasePrice(),
            salvageValue != null ? salvageValue : BigDecimal.ZERO,
            usefulLifeYears,
            elapsedYears));
  }

  @Transactional(readOnly = true)
  public List<DepreciationForecastDto> listForecastsForItem(UUID itemId) {
    return depreciationRecordRepository.findAllByItemIdOrderByCalculatedAtDesc(itemId).stream()
        .map(
            rec ->
                toForecastDto(
                    rec,
                    buildYearlySchedule(
                        rec.getInitialValue(),
                        rec.getSalvageValue(),
                        rec.getUsefulLifeYears(),
                        rec.getAnnualDepreciation())))
        .toList();
  }

  public List<YearlyDepreciationForecastDto> buildYearlySchedule(
      BigDecimal initialValue,
      BigDecimal salvageValue,
      int usefulLifeYears,
      BigDecimal annualDepreciation) {
    List<YearlyDepreciationForecastDto> schedule = new ArrayList<>(usefulLifeYears);
    BigDecimal opening = initialValue;
    BigDecimal accumulated = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    for (int year = 1; year <= usefulLifeYears; year++) {
      BigDecimal expense;
      if (year == usefulLifeYears) {
        expense =
            opening.subtract(salvageValue).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
      } else {
        expense = annualDepreciation.min(opening.subtract(salvageValue).max(BigDecimal.ZERO));
      }
      accumulated = accumulated.add(expense).setScale(2, RoundingMode.HALF_UP);
      BigDecimal closing = opening.subtract(expense).setScale(2, RoundingMode.HALF_UP);

      schedule.add(new YearlyDepreciationForecastDto(year, opening, expense, accumulated, closing));
      opening = closing;
    }
    return schedule;
  }

  @Transactional(readOnly = true)
  public List<DepreciationForecastDto> listAllForecasts() {
    return depreciationRecordRepository.findAll().stream()
        .map(
            rec ->
                financeMapper.toDto(
                    rec,
                    buildYearlySchedule(
                        rec.getInitialValue(),
                        rec.getSalvageValue(),
                        rec.getUsefulLifeYears(),
                        rec.getAnnualDepreciation())))
        .toList();
  }

  private DepreciationForecastDto toForecastDto(
      DepreciationRecord rec, List<YearlyDepreciationForecastDto> schedule) {
    return financeMapper.toDto(rec, schedule);
  }
}
