package uz.ttpu.inventory.integration.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.integration.api.OneCAssetRecordDto;
import uz.ttpu.inventory.integration.api.OneCReconciliationMismatchDto;
import uz.ttpu.inventory.integration.api.OneCReconciliationReportDto;
import uz.ttpu.inventory.integration.domain.ExternalSystemType;
import uz.ttpu.inventory.references.api.EmployeeDto;
import uz.ttpu.inventory.references.api.ReferenceSyncApi;

@Slf4j
@Service
@RequiredArgsConstructor
public class OneCReconciliationService {

  public static final String MISMATCH_FINANCIAL_VALUE = "FINANCIAL_VALUE_MISMATCH";
  public static final String MISMATCH_RESPONSIBLE_PERSON = "RESPONSIBLE_PERSON_MISMATCH";
  public static final String MISMATCH_NOT_FOUND_IN_1C = "NOT_FOUND_IN_1C";
  public static final String MISMATCH_NOT_FOUND_IN_LOCAL = "NOT_FOUND_IN_LOCAL";

  private final AssetLogisticsApi assetLogisticsApi;
  private final ReferenceSyncApi referenceSyncApi;
  private final OneCExternalClientStub oneCClientStub;
  private final CircuitBreakerRegistry circuitBreakerRegistry;
  private final RetryRegistry retryRegistry;

  private final AtomicReference<OneCReconciliationReportDto> latestReport = new AtomicReference<>();

  @Scheduled(cron = "${app.integration.1c.reconciliation-cron:0 0 3 * * *}", zone = "UTC")
  @SchedulerLock(
      name = "nightly1CReconciliationJob",
      lockAtLeastFor = "PT1M",
      lockAtMostFor = "PT15M")
  @Transactional(readOnly = true)
  public void runNightlyReconciliationJob() {
    log.info("Starting scheduled nightly full reconciliation against 1C Accounting...");
    OneCReconciliationReportDto report = reconcileWith1C();
    log.info(
        "Finished scheduled nightly 1C reconciliation: status={}, matched={}, mismatches={}",
        report.status(),
        report.matchedCount(),
        report.mismatchCount());
  }

  @Transactional(readOnly = true)
  public OneCReconciliationReportDto reconcileWith1C() {
    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(ExternalSystemType.ONE_C.name());
    Retry retry = retryRegistry.retry(ExternalSystemType.ONE_C.name());

    Supplier<List<OneCAssetRecordDto>> oneCSupplier =
        Retry.decorateSupplier(
            retry, CircuitBreaker.decorateSupplier(cb, oneCClientStub::fetch1CAssetRegistry));

    List<OneCAssetRecordDto> oneCRecords = oneCSupplier.get();
    List<ItemDto> localItems = assetLogisticsApi.listAllItemsForReporting();

    Map<String, OneCAssetRecordDto> oneCMap = new LinkedHashMap<>();
    if (oneCRecords != null) {
      for (OneCAssetRecordDto rec : oneCRecords) {
        if (rec.inventoryNumber() != null && !rec.inventoryNumber().isBlank()) {
          oneCMap.put(rec.inventoryNumber().trim().toUpperCase(Locale.ROOT), rec);
        }
      }
    }

    Map<String, ItemDto> localMap = new LinkedHashMap<>();
    if (localItems != null) {
      for (ItemDto item : localItems) {
        if (item.inventoryNumber() != null && !item.inventoryNumber().isBlank()) {
          localMap.put(item.inventoryNumber().trim().toUpperCase(Locale.ROOT), item);
        }
      }
    }

    List<OneCReconciliationMismatchDto> mismatches = new ArrayList<>();
    int matchedCount = 0;

    for (Map.Entry<String, ItemDto> entry : localMap.entrySet()) {
      String invNum = entry.getKey();
      ItemDto localItem = entry.getValue();

      OneCAssetRecordDto oneCRecord = oneCMap.get(invNum);
      if (oneCRecord == null) {
        log.warn("1C Reconciliation: Item [{}] exists locally but NOT FOUND IN 1C", invNum);
        mismatches.add(
            new OneCReconciliationMismatchDto(
                invNum,
                MISMATCH_NOT_FOUND_IN_1C,
                localItem.name() + " (Residual: " + getEffectiveLocalValue(localItem) + ")",
                null,
                "Item exists in digital inventory but is missing in 1C Accounting registry"));
        continue;
      }

      boolean itemHasMismatch = false;

      // 1. Compare financial value (residual value or purchase price vs 1C financial value)
      BigDecimal localValue = getEffectiveLocalValue(localItem);
      BigDecimal oneCValue = oneCRecord.financialValue();
      if (localValue != null && oneCValue != null) {
        BigDecimal diff = localValue.subtract(oneCValue).abs();
        if (diff.compareTo(new BigDecimal("0.01")) > 0) {
          itemHasMismatch = true;
          log.warn(
              "1C Reconciliation: Financial value mismatch for [{}]: local={}, 1C={}, diff={}",
              invNum,
              localValue,
              oneCValue,
              diff);
          mismatches.add(
              new OneCReconciliationMismatchDto(
                  invNum,
                  MISMATCH_FINANCIAL_VALUE,
                  localValue.toPlainString(),
                  oneCValue.toPlainString(),
                  "Financial residual value discrepancy between digital inventory and 1C (diff: "
                      + diff
                      + ")"));
        }
      }

      // 2. Compare responsible person
      String localPersonSummary = resolveLocalResponsiblePerson(localItem.custodianEmployeeId());
      String oneCPerson = oneCRecord.responsiblePerson();
      if (!matchesResponsiblePerson(localPersonSummary, oneCPerson)) {
        itemHasMismatch = true;
        log.warn(
            "1C Reconciliation: Responsible person mismatch for [{}]: local='{}', 1C='{}'",
            invNum,
            localPersonSummary,
            oneCPerson);
        mismatches.add(
            new OneCReconciliationMismatchDto(
                invNum,
                MISMATCH_RESPONSIBLE_PERSON,
                localPersonSummary,
                oneCPerson,
                "Responsible person in digital inventory does not match 1C responsible person"));
      }

      if (!itemHasMismatch) {
        matchedCount++;
      }
    }

    // Check items present in 1C but missing in local system
    for (Map.Entry<String, OneCAssetRecordDto> entry : oneCMap.entrySet()) {
      String invNum = entry.getKey();
      if (!localMap.containsKey(invNum)) {
        OneCAssetRecordDto oneCRecord = entry.getValue();
        log.warn("1C Reconciliation: Item [{}] exists in 1C but NOT FOUND IN LOCAL SYSTEM", invNum);
        mismatches.add(
            new OneCReconciliationMismatchDto(
                invNum,
                MISMATCH_NOT_FOUND_IN_LOCAL,
                null,
                oneCRecord.name() + " (Value: " + oneCRecord.financialValue() + ")",
                "Item exists in 1C Accounting registry but is missing in digital inventory"
                    + " system"));
      }
    }

    String status = mismatches.isEmpty() ? "CLEAN" : "DISCREPANCIES_FOUND";
    OneCReconciliationReportDto report =
        new OneCReconciliationReportDto(
            UUID.randomUUID(),
            Instant.now(),
            localMap.size(),
            oneCMap.size(),
            matchedCount,
            mismatches.size(),
            mismatches,
            status);

    latestReport.set(report);
    return report;
  }

  public Optional<OneCReconciliationReportDto> getLatestReport() {
    return Optional.ofNullable(latestReport.get());
  }

  private BigDecimal getEffectiveLocalValue(ItemDto item) {
    if (item.residualValue() != null) {
      return item.residualValue();
    }
    return item.purchasePrice() != null ? item.purchasePrice() : BigDecimal.ZERO;
  }

  private String resolveLocalResponsiblePerson(UUID custodianEmployeeId) {
    if (custodianEmployeeId == null) {
      return "UNASSIGNED";
    }
    Optional<EmployeeDto> empOpt = referenceSyncApi.findEmployeeById(custodianEmployeeId);
    if (empOpt.isPresent()) {
      EmployeeDto emp = empOpt.get();
      return emp.employeeCode() + " - " + emp.fullName();
    }
    return custodianEmployeeId.toString();
  }

  private boolean matchesResponsiblePerson(String localSummary, String oneCPerson) {
    if (localSummary == null && oneCPerson == null) {
      return true;
    }
    if (localSummary == null || oneCPerson == null) {
      return false;
    }
    String normLocal = localSummary.trim().toLowerCase(Locale.ROOT);
    String normOneC = oneCPerson.trim().toLowerCase(Locale.ROOT);

    if (normLocal.equals(normOneC)
        || normLocal.contains(normOneC)
        || normOneC.contains(normLocal)) {
      return true;
    }

    // Split words / tokens (e.g. employee code or surname)
    String[] tokens = normOneC.split("[\\s,-]+");
    for (String token : tokens) {
      if (token.length() > 3 && normLocal.contains(token)) {
        return true;
      }
    }
    return false;
  }
}
