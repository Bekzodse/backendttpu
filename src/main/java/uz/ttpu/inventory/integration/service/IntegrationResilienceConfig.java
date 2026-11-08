package uz.ttpu.inventory.integration.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.ttpu.inventory.integration.domain.ExternalSystemType;
import uz.ttpu.inventory.integration.domain.ExternalSystemUnavailableException;

@Slf4j
@Configuration
public class IntegrationResilienceConfig {

  @Bean
  public RetryRegistry integrationRetryRegistry(
      @Value("${app.integration.resilience.retry-max-attempts:3}") int retryMaxAttempts,
      @Value("${app.integration.resilience.retry-wait-ms:50}") long retryWaitMs) {
    RetryConfig retryConfig =
        RetryConfig.custom()
            .maxAttempts(retryMaxAttempts)
            .waitDuration(Duration.ofMillis(retryWaitMs))
            .retryExceptions(ExternalSystemUnavailableException.class, RuntimeException.class)
            .ignoreExceptions(CallNotPermittedException.class)
            .build();
    RetryRegistry registry = RetryRegistry.of(retryConfig);

    for (ExternalSystemType type : ExternalSystemType.values()) {
      Retry retry = registry.retry(type.name());
      retry
          .getEventPublisher()
          .onRetry(
              event ->
                  log.warn(
                      "Resilience4j [Retry] attempt #{} for target {} due to: {}",
                      event.getNumberOfRetryAttempts(),
                      type.name(),
                      event.getLastThrowable() != null
                          ? event.getLastThrowable().getMessage()
                          : "unknown"))
          .onError(
              event ->
                  log.error(
                      "Resilience4j [Retry] exhausted ({} attempts) for target {}: {}",
                      event.getNumberOfRetryAttempts(),
                      type.name(),
                      event.getLastThrowable() != null
                          ? event.getLastThrowable().getMessage()
                          : "unknown"));
    }
    registry.retry("outboxWorker");
    return registry;
  }

  public CircuitBreakerRegistry integrationCircuitBreakerRegistry(
      float failureRateThreshold,
      int minimumNumberOfCalls,
      int slidingWindowSize,
      long waitOpenSeconds) {
    return integrationCircuitBreakerRegistry(
        failureRateThreshold, minimumNumberOfCalls, slidingWindowSize, waitOpenSeconds, 2);
  }

  @Bean
  public CircuitBreakerRegistry integrationCircuitBreakerRegistry(
      @Value("${app.integration.resilience.cb-failure-rate-threshold:50.0}")
          float failureRateThreshold,
      @Value("${app.integration.resilience.cb-minimum-calls:4}") int minimumNumberOfCalls,
      @Value("${app.integration.resilience.cb-sliding-window-size:6}") int slidingWindowSize,
      @Value("${app.integration.resilience.cb-wait-open-seconds:30}") long waitOpenSeconds,
      @Value("${app.integration.resilience.cb-permitted-calls-in-half-open:2}")
          int permittedCallsInHalfOpen) {
    CircuitBreakerConfig cbConfig =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(failureRateThreshold)
            .minimumNumberOfCalls(minimumNumberOfCalls)
            .slidingWindowSize(slidingWindowSize)
            .waitDurationInOpenState(Duration.ofSeconds(waitOpenSeconds))
            .permittedNumberOfCallsInHalfOpenState(permittedCallsInHalfOpen)
            .recordExceptions(ExternalSystemUnavailableException.class, RuntimeException.class)
            .build();
    CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(cbConfig);

    for (ExternalSystemType type : ExternalSystemType.values()) {
      CircuitBreaker cb = registry.circuitBreaker(type.name());
      cb.getEventPublisher()
          .onStateTransition(
              event ->
                  log.info(
                      "Resilience4j [CircuitBreaker] state transition for {}: {} -> {}",
                      type.name(),
                      event.getStateTransition().getFromState(),
                      event.getStateTransition().getToState()))
          .onError(
              event ->
                  log.warn(
                      "Resilience4j [CircuitBreaker] recorded call failure for {}: {}",
                      type.name(),
                      event.getThrowable() != null
                          ? event.getThrowable().getMessage()
                          : "unknown"));
    }
    registry.circuitBreaker("outboxWorker");
    return registry;
  }
}
