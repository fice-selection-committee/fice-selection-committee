package edu.kpi.fice.telegram_service.health;

import edu.kpi.fice.telegram_service.config.TelegramProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Health indicator that verifies the Telegram bot token is valid by calling getMe. */
@Slf4j
@Component
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "true")
public class TelegramHealthIndicator implements HealthIndicator {

  private static final String API_BASE = "https://api.telegram.org/bot";

  private final String botToken;
  private final RestTemplate restTemplate;

  public TelegramHealthIndicator(TelegramProperties properties) {
    this.botToken = properties.bot().token();
    this.restTemplate = new RestTemplate();
  }

  @Override
  public Health health() {
    try {
      String url = API_BASE + botToken + "/getMe";
      var response = restTemplate.getForEntity(url, String.class);
      if (response.getStatusCode().is2xxSuccessful()) {
        return Health.up().withDetail("telegram", "Bot token valid").build();
      }
      return Health.down()
          .withDetail("telegram", "Unexpected status: " + response.getStatusCode())
          .build();
    } catch (Exception e) {
      return Health.down().withDetail("telegram", "Failed: " + e.getMessage()).build();
    }
  }
}
