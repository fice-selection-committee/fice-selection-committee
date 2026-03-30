package edu.kpi.fice.telegram_service.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(BotProperties bot, RateLimitProperties rateLimit) {

  public record BotProperties(String token, boolean enabled, List<Long> allowedChatIds) {
    public BotProperties {
      if (allowedChatIds == null) allowedChatIds = List.of();
    }

    public boolean isChatAllowed(long chatId) {
      return allowedChatIds.isEmpty() || allowedChatIds.contains(chatId);
    }
  }

  public record RateLimitProperties(int maxRequestsPerSecond) {}
}
