package uz.ttpu.inventory.integration.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.integration.api.EmployeeDismissedEvent;
import uz.ttpu.inventory.integration.api.HemisDepartmentPayloadDto;
import uz.ttpu.inventory.integration.api.HemisDismissalResultDto;
import uz.ttpu.inventory.integration.api.HemisEmployeePayloadDto;
import uz.ttpu.inventory.integration.api.HemisSyncResultDto;
import uz.ttpu.inventory.integration.domain.ExternalSystemType;
import uz.ttpu.inventory.references.api.ReferenceSyncApi;

@Service
public class HemisSyncService {

  private final HemisExternalClientStub hemisClientStub;
  private final ReferenceSyncApi referenceSyncApi;
  private final ApplicationEventPublisher eventPublisher;
  private final RetryRegistry retryRegistry;
  private final CircuitBreakerRegistry circuitBreakerRegistry;

  @org.springframework.beans.factory.annotation.Autowired
  public HemisSyncService(
      HemisExternalClientStub hemisClientStub,
      ReferenceSyncApi referenceSyncApi,
      ApplicationEventPublisher eventPublisher,
      RetryRegistry retryRegistry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    this.hemisClientStub = hemisClientStub;
    this.referenceSyncApi = referenceSyncApi;
    this.eventPublisher = eventPublisher;
    this.retryRegistry = retryRegistry;
    this.circuitBreakerRegistry = circuitBreakerRegistry;
  }

  public HemisSyncService(
      HemisExternalClientStub hemisClientStub,
      ReferenceSyncApi referenceSyncApi,
      RetryRegistry retryRegistry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    this(
        hemisClientStub,
        referenceSyncApi,
        (ApplicationEventPublisher) null,
        retryRegistry,
        circuitBreakerRegistry);
  }

  @Scheduled(cron = "${app.integration.hemis.sync-cron:0 0 2 * * *}", zone = "UTC")
  @SchedulerLock(name = "dailyHemisSyncJob", lockAtLeastFor = "PT1M", lockAtMostFor = "PT15M")
  @Transactional
  public void runDailyHemisSyncJob() {
    syncDailyHemisUpdates();
  }

  @Transactional
  public HemisSyncResultDto syncDailyHemisUpdates() {
    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(ExternalSystemType.HEMIS.name());
    Retry retry = retryRegistry.retry(ExternalSystemType.HEMIS.name());

    Supplier<List<HemisDepartmentPayloadDto>> deptSupplier =
        Retry.decorateSupplier(
            retry,
            CircuitBreaker.decorateSupplier(cb, hemisClientStub::fetchDailyDepartmentUpdates));
    Supplier<List<HemisEmployeePayloadDto>> empSupplier =
        Retry.decorateSupplier(
            retry, CircuitBreaker.decorateSupplier(cb, hemisClientStub::fetchDailyEmployeeUpdates));

    List<HemisDepartmentPayloadDto> departments = deptSupplier.get();
    List<HemisEmployeePayloadDto> employees = empSupplier.get();
    return applyHemisUpdates(departments, employees);
  }

  @Transactional
  public HemisSyncResultDto applyHemisUpdates(
      List<HemisDepartmentPayloadDto> departments, List<HemisEmployeePayloadDto> employees) {
    int deptCount = 0;
    if (departments != null) {
      for (HemisDepartmentPayloadDto dept : departments) {
        referenceSyncApi.upsertDepartmentFromHemis(dept.code(), dept.name());
        deptCount++;
      }
    }

    int empCount = 0;
    if (employees != null) {
      for (HemisEmployeePayloadDto emp : employees) {
        referenceSyncApi.upsertEmployeeFromHemis(
            emp.hemisId(),
            emp.employeeCode(),
            emp.firstName(),
            emp.lastName(),
            emp.middleName(),
            emp.email(),
            emp.phone(),
            emp.position(),
            emp.departmentCode());
        empCount++;
      }
    }

    return new HemisSyncResultDto(deptCount, empCount, Instant.now());
  }

  @Transactional
  public HemisDismissalResultDto dismissEmployee(
      UUID employeeId, String orderNumber, String reason) {
    String effectiveOrderNumber =
        orderNumber != null ? orderNumber : "HEMIS-DISMISS-" + Instant.now().toEpochMilli();
    String effectiveReason = reason != null ? reason : "Employment terminated in HEMIS";
    Instant now = Instant.now();

    if (eventPublisher != null) {
      eventPublisher.publishEvent(
          new EmployeeDismissedEvent(employeeId, effectiveOrderNumber, effectiveReason, now));
    }

    return new HemisDismissalResultDto(
        employeeId, effectiveOrderNumber, "DISMISSAL_INITIATED", now);
  }
}
