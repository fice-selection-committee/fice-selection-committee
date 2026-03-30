package edu.kpi.fice.telegram_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import edu.kpi.fice.telegram_service.config.TelegramProperties;
import edu.kpi.fice.telegram_service.config.TelegramProperties.BotProperties;
import edu.kpi.fice.telegram_service.config.TelegramProperties.RateLimitProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RateLimitService")
class RateLimitServiceTest {

  private RateLimitService rateLimitService;

  @BeforeEach
  void setUp() {
    TelegramProperties props =
        new TelegramProperties(
            new BotProperties("test-token", true, List.of()), new RateLimitProperties(5));
    rateLimitService = new RateLimitService(props);
  }

  @Nested
  @DisplayName("isChatRateLimited")
  class ChatRateLimit {

    @Test
    @DisplayName("allows requests within limit")
    void isChatRateLimited_allowsWithinLimit() {
      for (int i = 0; i < 5; i++) {
        assertThat(rateLimitService.isChatRateLimited(1L)).isFalse();
      }
    }

    @Test
    @DisplayName("blocks requests exceeding limit")
    void isChatRateLimited_blocksExceedingLimit() {
      for (int i = 0; i < 5; i++) {
        rateLimitService.isChatRateLimited(1L);
      }
      assertThat(rateLimitService.isChatRateLimited(1L)).isTrue();
    }

    @Test
    @DisplayName("different chats have independent limits")
    void isChatRateLimited_independentPerChat() {
      for (int i = 0; i < 5; i++) {
        rateLimitService.isChatRateLimited(1L);
      }
      // Chat 2 should still have its own limit
      assertThat(rateLimitService.isChatRateLimited(2L)).isFalse();
    }
  }

  @Nested
  @DisplayName("isWriteRateLimited")
  class WriteRateLimit {

    @Test
    @DisplayName("allows first write")
    void isWriteRateLimited_allowsFirstWrite() {
      assertThat(rateLimitService.isWriteRateLimited(1L)).isFalse();
    }

    @Test
    @DisplayName("blocks second write within cooldown")
    void isWriteRateLimited_blocksWithinCooldown() {
      rateLimitService.recordWrite(1L);
      assertThat(rateLimitService.isWriteRateLimited(1L)).isTrue();
    }

    @Test
    @DisplayName("different users have independent cooldowns")
    void isWriteRateLimited_independentPerUser() {
      rateLimitService.recordWrite(1L);
      assertThat(rateLimitService.isWriteRateLimited(2L)).isFalse();
    }
  }
}
