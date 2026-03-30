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
public class SearchFlagsHandler implements BotCommandHandler {

  private final EnvironmentServiceClient envClient;
  private final BotMessageResolver msg;

  @Override
  public boolean supports(String command) {
    return "/search".equals(command);
  }

  @Override
  public BotResponse handle(String[] args, Locale locale) {
    if (args.length == 0) {
      return BotResponse.text(msg.msg(locale, "flags.search.usage"));
    }

    String query = String.join(" ", args).toLowerCase();
    try {
      List<FeatureFlagResponse> flags = envClient.getFlags(null, null);

      List<FeatureFlagResponse> matched =
          flags.stream()
              .filter(
                  f ->
                      f.key().toLowerCase().contains(query)
                          || (f.description() != null
                              && f.description().toLowerCase().contains(query)))
              .toList();

      if (matched.isEmpty()) {
        return BotResponse.text(msg.msg(locale, "flags.search.empty", query));
      }

      var sb =
          new StringBuilder(
              "<b>"
                  + msg.msg(locale, "flags.search.title", "<code>" + query + "</code>")
                  + "</b>\n\n");
      for (var flag : matched) {
        String icon = Boolean.TRUE.equals(flag.enabled()) ? "\uD83D\uDFE2" : "\uD83D\uDD34";
        sb.append(icon).append(" <code>").append(flag.key()).append("</code>");
        if (flag.description() != null) {
          sb.append(" \u2014 ").append(flag.description());
        }
        sb.append("\n");
      }
      sb.append("\n").append(msg.msg(locale, "flags.search.found", matched.size()));
      return BotResponse.text(sb.toString());
    } catch (Exception e) {
      log.error("Failed to search flags for '{}': {}", query, e.getMessage());
      return BotResponse.text(msg.msg(locale, "flags.search.error"));
    }
  }
}
