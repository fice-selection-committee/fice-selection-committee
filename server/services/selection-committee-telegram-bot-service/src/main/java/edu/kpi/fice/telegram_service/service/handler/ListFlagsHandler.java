package edu.kpi.fice.telegram_service.service.handler;

import edu.kpi.fice.telegram_service.client.EnvironmentServiceClient;
import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.dto.FeatureFlagResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListFlagsHandler implements BotCommandHandler {

  private final EnvironmentServiceClient envClient;
  private final BotMessageResolver msg;

  @Override
  public boolean supports(String command) {
    return "/flags".equals(command);
  }

  @Override
  public BotResponse handle(String[] args, Locale locale) {
    try {
      List<FeatureFlagResponse> flags = envClient.getFlags(null, null);

      if (flags.isEmpty()) {
        return BotResponse.text(msg.msg(locale, "flags.list.empty"));
      }

      var sb =
          new StringBuilder("<b>\uD83D\uDEA9 " + msg.msg(locale, "flags.list.title") + "</b>\n\n");
      for (var flag : flags) {
        String icon = Boolean.TRUE.equals(flag.enabled()) ? "\uD83D\uDFE2" : "\uD83D\uDD34";
        sb.append(icon).append(" <code>").append(flag.key()).append("</code>");
        if (flag.status() != null) {
          sb.append(" [").append(flag.status()).append("]");
        }
        if (flag.flagType() != null) {
          sb.append(" (").append(flag.flagType()).append(")");
        }
        sb.append("\n");
      }
      sb.append("\n").append(msg.msg(locale, "flags.list.total", flags.size()));
      return BotResponse.text(sb.toString());
    } catch (Exception e) {
      log.error("Failed to list flags: {}", e.getMessage());
      return BotResponse.text(msg.msg(locale, "flags.list.error"));
    }
  }
}
