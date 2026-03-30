package edu.kpi.fice.telegram_service.service.handler;

import edu.kpi.fice.telegram_service.client.EnvironmentServiceClient;
import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.dto.FeatureFlagResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import edu.kpi.fice.telegram_service.security.BotAuthorizationService;
import edu.kpi.fice.telegram_service.service.BotUserService;
import edu.kpi.fice.telegram_service.service.PendingToggleStore;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToggleFlagHandler implements BotCommandHandler {

  private final EnvironmentServiceClient envClient;
  private final PendingToggleStore pendingToggleStore;
  private final BotMessageResolver msg;
  private final BotAuthorizationService authService;
  private final BotUserService botUserService;

  @Override
  public boolean supports(String command) {
    return "/toggle".equals(command);
  }

  @Override
  public BotResponse handle(String[] args, Locale locale) {
    if (args.length == 0) {
      return BotResponse.text(msg.msg(locale, "flags.toggle.usage"));
    }

    // Extract userId from injected arg for authorization
    Long userId = extractUserId(args);
    if (userId != null && !authService.canToggleFlags(userId)) {
      return BotResponse.text(msg.msg(locale, "flags.toggle.unauthorized"));
    }

    String key = args[0];

    // Check for "confirm" or "cancel" as second arg
    if (args.length >= 2) {
      String secondArg = args[1];
      if (!secondArg.startsWith("__userId__")) {
        if ("confirm".equalsIgnoreCase(secondArg)) {
          return executeToggle(key, userId, locale);
        }
        if ("cancel".equalsIgnoreCase(secondArg)) {
          pendingToggleStore.remove(key);
          return BotResponse.text(msg.msg(locale, "flags.toggle.cancelled", key));
        }
      }
    }

    // Show confirmation prompt
    try {
      FeatureFlagResponse flag = envClient.getFlagByKey(key);
      boolean newState = !Boolean.TRUE.equals(flag.enabled());
      pendingToggleStore.store(key, newState);

      String currentIcon =
          Boolean.TRUE.equals(flag.enabled())
              ? "\uD83D\uDFE2 " + msg.msg(locale, "state.on")
              : "\uD83D\uDD34 " + msg.msg(locale, "state.off");
      String newIcon =
          newState
              ? "\uD83D\uDFE2 " + msg.msg(locale, "state.on")
              : "\uD83D\uDD34 " + msg.msg(locale, "state.off");

      return BotResponse.text(
          "<b>"
              + msg.msg(locale, "flags.toggle.confirm.title")
              + "</b>\n\n"
              + "Flag: <code>"
              + key
              + "</code>\n"
              + msg.msg(locale, "flags.toggle.confirm.current", currentIcon)
              + "\n"
              + msg.msg(locale, "flags.toggle.confirm.new", newIcon)
              + "\n\n"
              + "Reply with:\n"
              + "<code>/toggle "
              + key
              + " confirm</code> \u2014 to proceed\n"
              + "<code>/toggle "
              + key
              + " cancel</code> \u2014 to abort");
    } catch (Exception e) {
      log.error("Failed to get flag '{}' for toggle: {}", key, e.getMessage());
      return BotResponse.text(msg.msg(locale, "flags.view.error", key));
    }
  }

  private BotResponse executeToggle(String key, Long userId, Locale locale) {
    Boolean newState = pendingToggleStore.get(key);
    if (newState == null) {
      return BotResponse.text(msg.msg(locale, "flags.toggle.no_pending", key));
    }

    try {
      String actorName = userId != null ? botUserService.getDisplayName(userId) : "telegram-bot";
      envClient.toggleFlag(key, newState, actorName);
      pendingToggleStore.remove(key);

      String icon =
          newState
              ? "\uD83D\uDFE2 " + msg.msg(locale, "state.on")
              : "\uD83D\uDD34 " + msg.msg(locale, "state.off");
      return BotResponse.text(msg.msg(locale, "flags.toggle.success", key, icon));
    } catch (Exception e) {
      log.error("Failed to toggle flag '{}': {}", key, e.getMessage());
      pendingToggleStore.remove(key);
      return BotResponse.text(msg.msg(locale, "flags.toggle.error", key));
    }
  }

  private Long extractUserId(String[] args) {
    for (String arg : args) {
      if (arg != null && arg.startsWith("__userId__")) {
        try {
          return Long.parseLong(arg.substring("__userId__".length()));
        } catch (NumberFormatException e) {
          return null;
        }
      }
    }
    return null;
  }
}
