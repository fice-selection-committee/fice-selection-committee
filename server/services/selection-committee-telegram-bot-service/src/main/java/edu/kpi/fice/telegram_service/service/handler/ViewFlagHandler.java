package edu.kpi.fice.telegram_service.service.handler;

import edu.kpi.fice.telegram_service.client.EnvironmentServiceClient;
import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.dto.FeatureFlagResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewFlagHandler implements BotCommandHandler {

  private final EnvironmentServiceClient envClient;
  private final BotMessageResolver msg;

  @Override
  public boolean supports(String command) {
    return "/flag".equals(command);
  }

  @Override
  public BotResponse handle(String[] args, Locale locale) {
    if (args.length == 0) {
      return BotResponse.text(msg.msg(locale, "flags.view.usage"));
    }

    String key = args[0];
    try {
      FeatureFlagResponse flag = envClient.getFlagByKey(key);

      var sb = new StringBuilder();
      sb.append("<b>").append(msg.msg(locale, "flags.view.title", flag.key())).append("</b>\n\n");

      String stateIcon = Boolean.TRUE.equals(flag.enabled()) ? "\uD83D\uDFE2" : "\uD83D\uDD34";
      String stateText =
          Boolean.TRUE.equals(flag.enabled())
              ? msg.msg(locale, "flags.view.state.enabled")
              : msg.msg(locale, "flags.view.state.disabled");
      sb.append("<b>")
          .append(msg.msg(locale, "flags.view.field.state"))
          .append(":</b> ")
          .append(stateIcon)
          .append(" ")
          .append(stateText)
          .append("\n");

      if (flag.status() != null) {
        sb.append("<b>")
            .append(msg.msg(locale, "flags.view.field.status"))
            .append(":</b> ")
            .append(flag.status())
            .append("\n");
      }
      if (flag.flagType() != null) {
        sb.append("<b>")
            .append(msg.msg(locale, "flags.view.field.type"))
            .append(":</b> ")
            .append(flag.flagType())
            .append("\n");
      }
      if (flag.owner() != null) {
        sb.append("<b>")
            .append(msg.msg(locale, "flags.view.field.owner"))
            .append(":</b> ")
            .append(flag.owner())
            .append("\n");
      }
      if (flag.description() != null) {
        sb.append("<b>")
            .append(msg.msg(locale, "flags.view.field.description"))
            .append(":</b> ")
            .append(flag.description())
            .append("\n");
      }
      if (flag.rolloutPercentage() != null && flag.rolloutPercentage() < 100) {
        sb.append("<b>")
            .append(msg.msg(locale, "flags.view.field.rollout"))
            .append(":</b> ")
            .append(flag.rolloutPercentage())
            .append("%\n");
      }
      if (flag.expiresAt() != null) {
        sb.append("<b>")
            .append(msg.msg(locale, "flags.view.field.expires"))
            .append(":</b> ")
            .append(flag.expiresAt())
            .append("\n");
      }

      return BotResponse.text(sb.toString());
    } catch (Exception e) {
      log.error("Failed to get flag '{}': {}", key, e.getMessage());
      return BotResponse.text(msg.msg(locale, "flags.view.error", key));
    }
  }
}
