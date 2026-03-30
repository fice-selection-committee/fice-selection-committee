package edu.kpi.fice.telegram_service.client;

import edu.kpi.fice.telegram_service.dto.FeatureFlagResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Circuit-breaker-protected wrapper around EnvironmentServiceClient. Provides fallback behavior for
 * read operations and error messages for write operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientEnvironmentServiceClient {

  private final EnvironmentServiceClient delegate;

  /** Cached last-known flag list for fallback during outages. */
  private final List<FeatureFlagResponse> cachedFlags = new CopyOnWriteArrayList<>();

  @CircuitBreaker(name = "environmentService", fallbackMethod = "getFlagsFallback")
  public List<FeatureFlagResponse> getFlags(String environment, String scope) {
    List<FeatureFlagResponse> flags = delegate.getFlags(environment, scope);
    // Update cache on success
    cachedFlags.clear();
    cachedFlags.addAll(flags);
    return flags;
  }

  @CircuitBreaker(name = "environmentService", fallbackMethod = "getFlagByKeyFallback")
  public FeatureFlagResponse getFlagByKey(String key) {
    return delegate.getFlagByKey(key);
  }

  @CircuitBreaker(name = "environmentService", fallbackMethod = "toggleFlagFallback")
  public FeatureFlagResponse toggleFlag(String key, boolean enabled, String actorId) {
    return delegate.toggleFlag(key, enabled, actorId);
  }

  @SuppressWarnings("unused")
  List<FeatureFlagResponse> getFlagsFallback(
      String environment, String scope, Throwable throwable) {
    log.warn(
        "Circuit breaker fallback for getFlags: {} — returning cached list ({} entries)",
        throwable.getMessage(),
        cachedFlags.size());
    return Collections.unmodifiableList(cachedFlags);
  }

  @SuppressWarnings("unused")
  FeatureFlagResponse getFlagByKeyFallback(String key, Throwable throwable) {
    log.warn("Circuit breaker fallback for getFlagByKey '{}': {}", key, throwable.getMessage());
    // Try to find in cache
    return cachedFlags.stream()
        .filter(f -> key.equals(f.key()))
        .findFirst()
        .orElseThrow(
            () ->
                new RuntimeException(
                    "Service unavailable and flag not in cache: " + key, throwable));
  }

  @SuppressWarnings("unused")
  FeatureFlagResponse toggleFlagFallback(
      String key, boolean enabled, String actorId, Throwable throwable) {
    log.warn("Circuit breaker fallback for toggleFlag '{}': {}", key, throwable.getMessage());
    throw new RuntimeException(
        "Cannot toggle flag — environment service is unavailable", throwable);
  }
}
