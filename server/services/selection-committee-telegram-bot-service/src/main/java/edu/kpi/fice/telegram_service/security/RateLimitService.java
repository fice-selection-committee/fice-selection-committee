package edu.kpi.fice.telegram_service.security;

import edu.kpi.fice.telegram_service.config.TelegramProperties;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Per-chat and per-user rate limiting for bot operations. */
@Slf4j
@Service
public class RateLimitService {

  private static final long WRITE_COOLDOWN_SECONDS = 60;

  private final int maxRequestsPerSecond;
  private final ConcurrentHashMap<Long, Deque<Instant>> chatRequests = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, Instant> lastWriteByUser = new ConcurrentHashMap<>();

  public RateLimitService(TelegramProperties properties) {
    this.maxRequestsPerSecond = properties.rateLimit().maxRequestsPerSecond();
  }

  /** Returns true if the chat has exceeded the per-second request limit. */
  public boolean isChatRateLimited(Long chatId) {
    Instant now = Instant.now();
    Instant windowStart = now.minusSeconds(1);

    Deque<Instant> timestamps =
        chatRequests.computeIfAbsent(chatId, k -> new ConcurrentLinkedDeque<>());

    // Remove old entries outside the 1-second window
    while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
      timestamps.pollFirst();
    }

    if (timestamps.size() >= maxRequestsPerSecond) {
      log.debug("Chat {} rate limited: {} requests in last second", chatId, timestamps.size());
      return true;
    }

    timestamps.addLast(now);
    return false;
  }

  /** Returns true if the user has exceeded the write (toggle) cooldown. */
  public boolean isWriteRateLimited(Long userId) {
    Instant now = Instant.now();
    Instant lastWrite = lastWriteByUser.get(userId);

    if (lastWrite != null && lastWrite.plusSeconds(WRITE_COOLDOWN_SECONDS).isAfter(now)) {
      log.debug(
          "User {} write rate limited: last write was {}s ago",
          userId,
          java.time.Duration.between(lastWrite, now).getSeconds());
      return true;
    }

    return false;
  }

  /** Records that a write operation occurred for a user. */
  public void recordWrite(Long userId) {
    lastWriteByUser.put(userId, Instant.now());
  }
}
