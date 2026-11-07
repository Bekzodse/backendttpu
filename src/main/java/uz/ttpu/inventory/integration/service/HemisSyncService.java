package uz.ttpu.inventory.integration.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.integration.api.HemisDepartmentPayloadDto;
import uz.ttpu.inventory.integration.api.HemisEmployeePayloadDto;
import uz.ttpu.inventory.integration.api.HemisSyncResultDto;
import uz.ttpu.inventory.integration.domain.ExternalSystemType;
import uz.ttpu.inventory.references.api.ReferenceSyncApi;

@Service
@RequiredArgsConstructor
public class HemisSyncService {

    private final HemisExternalClientStub hemisClientStub;
    private final ReferenceSyncApi referenceSyncApi;
    private final RetryRegistry retryRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

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

        Supplier<List<HemisDepartmentPayloadDto>> deptSupplier = Retry.decorateSupplier(
                retry,
                CircuitBreaker.decorateSupplier(cb, hemisClientStub::fetchDailyDepartmentUpdates)
        );
        Supplier<List<HemisEmployeePayloadDto>> empSupplier = Retry.decorateSupplier(
                retry,
                CircuitBreaker.decorateSupplier(cb, hemisClientStub::fetchDailyEmployeeUpdates)
        );

        List<HemisDepartmentPayloadDto> departments = deptSupplier.get();
        List<HemisEmployeePayloadDto> employees = empSupplier.get();
        return applyHemisUpdates(departments, employees);
    }

    @Transactional
    public HemisSyncResultDto applyHemisUpdates(
            List<HemisDepartmentPayloadDto> departments,
            List<HemisEmployeePayloadDto> employees
    ) {
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
                        emp.departmentCode()
                );
                empCount++;
            }
        }

        return new HemisSyncResultDto(deptCount, empCount, Instant.now());
    }
}
