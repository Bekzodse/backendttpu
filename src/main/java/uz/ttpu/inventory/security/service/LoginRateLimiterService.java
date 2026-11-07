package uz.ttpu.inventory.security.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.ttpu.inventory.security.domain.RateLimitExceededException;

@Service
@RequiredArgsConstructor
public class LoginRateLimiterService {

  private final SecurityProperties securityProperties;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  public ConsumptionProbe tryConsume(String clientKey) {
    String normalizedKey =
        (clientKey != null && !clientKey.isBlank()) ? clientKey.trim() : "anonymous";
    Bucket bucket = buckets.computeIfAbsent(normalizedKey, this::createNewBucket);
    return bucket.tryConsumeAndReturnRemaining(1);
  }

  public long consumeOrThrow(String clientKey) {
    ConsumptionProbe probe = tryConsume(clientKey);
    if (!probe.isConsumed()) {
      long waitForRefillSeconds =
          Math.max(1L, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
      throw new RateLimitExceededException(
          "Rate limit exceeded for login endpoint. Please retry after "
              + waitForRefillSeconds
              + " seconds.",
          waitForRefillSeconds);
    }
    return probe.getRemainingTokens();
  }

  public void resetBucket(String clientKey) {
    if (clientKey != null) {
      buckets.remove(clientKey.trim());
    }
  }

  public void clearAllBuckets() {
    buckets.clear();
  }

  private Bucket createNewBucket(String key) {
    int capacity = Math.max(1, securityProperties.getLoginRateLimitCapacity());
    Duration window =
        securityProperties.getLoginRateLimitWindow() != null
            ? securityProperties.getLoginRateLimitWindow()
            : Duration.ofMinutes(1);

    Bandwidth limit = Bandwidth.builder().capacity(capacity).refillGreedy(capacity, window).build();

    return Bucket.builder().addLimit(limit).build();
  }
}
