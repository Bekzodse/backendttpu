package uz.ttpu.inventory.integration.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.ttpu.inventory.integration.domain.ExternalSystemUnavailableException;

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
    return RetryRegistry.of(retryConfig);
  }

  @Bean
  public CircuitBreakerRegistry integrationCircuitBreakerRegistry(
      @Value("${app.integration.resilience.cb-failure-rate-threshold:50.0}")
          float failureRateThreshold,
      @Value("${app.integration.resilience.cb-minimum-calls:4}") int minimumNumberOfCalls,
      @Value("${app.integration.resilience.cb-sliding-window-size:6}") int slidingWindowSize,
      @Value("${app.integration.resilience.cb-wait-open-seconds:30}") long waitOpenSeconds) {
    CircuitBreakerConfig cbConfig =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(failureRateThreshold)
            .minimumNumberOfCalls(minimumNumberOfCalls)
            .slidingWindowSize(slidingWindowSize)
            .waitDurationInOpenState(Duration.ofSeconds(waitOpenSeconds))
            .permittedNumberOfCallsInHalfOpenState(2)
            .recordExceptions(ExternalSystemUnavailableException.class, RuntimeException.class)
            .build();
    return CircuitBreakerRegistry.of(cbConfig);
  }
}
