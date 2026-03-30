package edu.kpi.fice.telegram_service.service.handler;

import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import edu.kpi.fice.telegram_service.keyboard.InlineKeyboardBuilder;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Handles system health monitoring via inline keyboard with configurable service URLs. */
@Slf4j
@Component
public class HealthCallbackHandler implements CallbackHandler {

  private final BotMessageResolver msg;
  private final RestTemplate healthRestTemplate;
  private final Map<String, String> serviceEndpoints;

  @Autowired
  public HealthCallbackHandler(
      BotMessageResolver msg, @Value("${bot.health-check.timeout-ms:2000}") int timeoutMs) {
    this(msg, null, timeoutMs);
  }

  public HealthCallbackHandler(
      BotMessageResolver msg, Map<String, String> serviceEndpoints, int timeoutMs) {
    this.msg = msg;
    this.serviceEndpoints =
        (serviceEndpoints == null || serviceEndpoints.isEmpty())
            ? getDefaultEndpoints()
            : new LinkedHashMap<>(serviceEndpoints);
    this.healthRestTemplate =
        new RestTemplateBuilder()
            .connectTimeout(Duration.ofMillis(timeoutMs))
            .readTimeout(Duration.ofMillis(timeoutMs))
            .build();
  }

  private static Map<String, String> getDefaultEndpoints() {
    var defaults = new LinkedHashMap<String, String>();
    defaults.put("Environment", "http://sc-environment:8085/actuator/health");
    defaults.put("Identity", "http://sc-identity:8081/actuator/health");
    defaults.put("Gateway", "http://sc-gateway:8080/actuator/health");
    defaults.put("Admission", "http://sc-admission:8083/actuator/health");
    defaults.put("Documents", "http://sc-documents:8084/actuator/health");
    defaults.put("Notifications", "http://sc-notifications:8086/actuator/health");
    return defaults;
  }

  @Override
  public boolean supports(String module) {
    return "health".equals(module);
  }

  @Override
  public BotResponse handle(
      String callbackData, Long userId, Long chatId, Integer messageId, Locale locale) {
    return handleServiceHealth(messageId, locale);
  }

  private BotResponse handleServiceHealth(Integer messageId, Locale locale) {
    var sb = new StringBuilder("<b>\uD83D\uDC9A " + msg.msg(locale, "health.title") + "</b>\n\n");
    Instant checkTime = Instant.now();

    for (var entry : serviceEndpoints.entrySet()) {
      String name = entry.getKey();
      String url = entry.getValue();
      long start = System.currentTimeMillis();
      boolean isUp = checkHealth(url);
      long elapsed = System.currentTimeMillis() - start;

      if (isUp) {
        sb.append(msg.msg(locale, "health.service.up", "\uD83D\uDC9A " + name));
        sb.append(" (").append(elapsed).append("ms)");
      } else {
        sb.append(msg.msg(locale, "health.service.down", "\uD83D\uDC94 " + name));
      }
      sb.append("\n");
    }

    String formattedTime =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.of("Europe/Kyiv"))
            .format(checkTime);
    sb.append("\n<i>Checked: ").append(formattedTime).append("</i>");

    var kb =
        InlineKeyboardBuilder.create()
            .button("\uD83D\uDD04 Refresh", "health:services")
            .row()
            .navRow(
                "\u2B05\uFE0F " + msg.msg(locale, "common.btn.back"),
                "menu:main",
                "\uD83C\uDFE0 " + msg.msg(locale, "common.btn.home"),
                "nav:home");

    return BotResponse.edit(sb.toString(), kb.build(), messageId);
  }

  private boolean checkHealth(String url) {
    try {
      var response = healthRestTemplate.getForEntity(url, String.class);
      return response.getStatusCode().is2xxSuccessful();
    } catch (Exception e) {
      return false;
    }
  }
}
