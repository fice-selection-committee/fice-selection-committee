package edu.kpi.fice.telegram_service.service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Short-lived store for pending toggle confirmations. Entries expire after 5 minutes to prevent
 * stale confirmations.
 */
@Slf4j
@Component
public class PendingToggleStore {

  private static final long TTL_SECONDS = 300; // 5 minutes

  private record PendingToggle(Boolean newState, Instant createdAt) {}

  private final Map<String, PendingToggle> pending = new ConcurrentHashMap<>();

  public void store(String flagKey, boolean newState) {
    pending.put(flagKey, new PendingToggle(newState, Instant.now()));
  }

  public Boolean get(String flagKey) {
    PendingToggle toggle = pending.get(flagKey);
    if (toggle == null) {
      return null;
    }
    if (toggle.createdAt.plusSeconds(TTL_SECONDS).isBefore(Instant.now())) {
      pending.remove(flagKey);
      return null;
    }
    return toggle.newState;
  }

  public void remove(String flagKey) {
    pending.remove(flagKey);
  }

  @Scheduled(fixedRate = 60_000) // Every minute
  void evictExpired() {
    Instant cutoff = Instant.now().minusSeconds(TTL_SECONDS);
    pending.entrySet().removeIf(e -> e.getValue().createdAt.isBefore(cutoff));
  }
}
