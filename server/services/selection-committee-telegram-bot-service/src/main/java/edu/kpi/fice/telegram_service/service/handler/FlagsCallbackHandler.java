package edu.kpi.fice.telegram_service.service.handler;

import edu.kpi.fice.telegram_service.client.EnvironmentServiceClient;
import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.dto.FeatureFlagResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import edu.kpi.fice.telegram_service.keyboard.InlineKeyboardBuilder;
import edu.kpi.fice.telegram_service.security.BotAuthorizationService;
import edu.kpi.fice.telegram_service.service.BotUserService;
import edu.kpi.fice.telegram_service.service.PendingToggleStore;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Callback handler for feature flag operations: list, view, toggle. */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlagsCallbackHandler implements CallbackHandler {

  private static final int PAGE_SIZE = 5;

  private final EnvironmentServiceClient envClient;
  private final PendingToggleStore pendingToggleStore;
  private final BotMessageResolver msg;
  private final BotAuthorizationService authService;
  private final BotUserService botUserService;

  @Override
  public boolean supports(String module) {
    return "flags".equals(module);
  }

  @Override
  public BotResponse handle(
      String callbackData, Long userId, Long chatId, Integer messageId, Locale locale) {
    String[] parts = callbackData.split(":");
    String action = parts.length > 1 ? parts[1] : "list";

    return switch (action) {
      case "list" -> {
        int page = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        yield handleList(page, messageId, locale);
      }
      case "view" -> {
        String key = parts.length > 2 ? parts[2] : "";
        yield handleView(key, messageId, locale);
      }
      case "toggle" -> {
        String key = parts.length > 2 ? parts[2] : "";
        if (!authService.canToggleFlags(userId)) {
          yield BotResponse.edit(
              msg.msg(locale, "flags.toggle.unauthorized"), List.of(), messageId);
        }
        yield handleTogglePrompt(key, messageId, locale);
      }
      case "toggle_confirm" -> {
        String key = parts.length > 2 ? parts[2] : "";
        if (!authService.canToggleFlags(userId)) {
          yield BotResponse.edit(
              msg.msg(locale, "flags.toggle.unauthorized"), List.of(), messageId);
        }
        yield handleToggleConfirm(key, userId, messageId, locale);
      }
      case "toggle_cancel" -> {
        String key = parts.length > 2 ? parts[2] : "";
        yield handleToggleCancel(key, messageId, locale);
      }
      default -> BotResponse.text(msg.msg(locale, "common.error.generic"));
    };
  }

  private BotResponse handleList(int page, Integer messageId, Locale locale) {
    try {
      List<FeatureFlagResponse> flags = envClient.getFlags(null, null);

      if (flags.isEmpty()) {
        return BotResponse.edit(msg.msg(locale, "flags.list.empty"), List.of(), messageId);
      }

      int totalPages = (int) Math.ceil((double) flags.size() / PAGE_SIZE);
      int start = page * PAGE_SIZE;
      int end = Math.min(start + PAGE_SIZE, flags.size());
      List<FeatureFlagResponse> pageFlags = flags.subList(start, end);

      var sb =
          new StringBuilder("<b>\uD83D\uDEA9 " + msg.msg(locale, "flags.list.title") + "</b>\n\n");
      for (var flag : pageFlags) {
        String icon = Boolean.TRUE.equals(flag.enabled()) ? "\uD83D\uDFE2" : "\uD83D\uDD34";
        sb.append(icon).append(" <code>").append(flag.key()).append("</code>");
        if (flag.flagType() != null) {
          sb.append(" [").append(flag.flagType()).append("]");
        }
        sb.append("\n");
      }
      sb.append("\n").append(msg.msg(locale, "flags.list.total", flags.size()));

      var kb = InlineKeyboardBuilder.create();

      // Flag buttons (each flag is a row)
      for (var flag : pageFlags) {
        String icon = Boolean.TRUE.equals(flag.enabled()) ? "\uD83D\uDFE2" : "\uD83D\uDD34";
        kb.button(icon + " " + flag.key(), "flags:view:" + flag.key()).row();
      }

      // Pagination
      if (totalPages > 1) {
        if (page > 0) {
          kb.button("\u25C0\uFE0F", "flags:list:" + (page - 1));
        }
        kb.button((page + 1) + "/" + totalPages, "flags:noop");
        if (page < totalPages - 1) {
          kb.button("\u25B6\uFE0F", "flags:list:" + (page + 1));
        }
        kb.row();
      }

      // Navigation
      kb.navRow(
          "\u2B05\uFE0F " + msg.msg(locale, "common.btn.back"),
          "menu:main",
          "\uD83C\uDFE0 " + msg.msg(locale, "common.btn.home"),
          "nav:home");

      return BotResponse.edit(sb.toString(), kb.build(), messageId);
    } catch (Exception e) {
      log.error("Failed to list flags: {}", e.getMessage());
      return BotResponse.edit(msg.msg(locale, "flags.list.error"), List.of(), messageId);
    }
  }

  private BotResponse handleView(String key, Integer messageId, Locale locale) {
    try {
      FeatureFlagResponse flag = envClient.getFlagByKey(key);

      var sb = new StringBuilder();
      sb.append("<b>\uD83D\uDEA9 ").append(flag.key()).append("</b>\n\n");

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

      var kb =
          InlineKeyboardBuilder.create()
              .button(
                  "\uD83D\uDD04 " + msg.msg(locale, "flags.view.btn.toggle"), "flags:toggle:" + key)
              .row()
              .navRow(
                  "\u2B05\uFE0F " + msg.msg(locale, "common.btn.back"),
                  "flags:list:0",
                  "\uD83C\uDFE0 " + msg.msg(locale, "common.btn.home"),
                  "nav:home");

      return BotResponse.edit(sb.toString(), kb.build(), messageId);
    } catch (Exception e) {
      log.error("Failed to view flag '{}': {}", key, e.getMessage());
      return BotResponse.edit(msg.msg(locale, "flags.view.error", key), List.of(), messageId);
    }
  }

  private BotResponse handleTogglePrompt(String key, Integer messageId, Locale locale) {
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

      String text =
          "<b>\u26A0\uFE0F "
              + msg.msg(locale, "flags.toggle.confirm.title")
              + "</b>\n\n"
              + "<b>Flag:</b> <code>"
              + key
              + "</code>\n"
              + msg.msg(locale, "flags.toggle.confirm.current", currentIcon)
              + "\n"
              + msg.msg(locale, "flags.toggle.confirm.new", newIcon)
              + "\n\n"
              + msg.msg(locale, "flags.toggle.confirm.prompt");

      var kb =
          InlineKeyboardBuilder.create()
              .button(
                  "\u2705 " + msg.msg(locale, "flags.toggle.btn.confirm"),
                  "flags:toggle_confirm:" + key)
              .button(
                  "\u274C " + msg.msg(locale, "flags.toggle.btn.cancel"),
                  "flags:toggle_cancel:" + key)
              .row()
              .build();

      return BotResponse.edit(text, kb, messageId);
    } catch (Exception e) {
      log.error("Failed to get flag '{}' for toggle: {}", key, e.getMessage());
      return BotResponse.edit(msg.msg(locale, "flags.view.error", key), List.of(), messageId);
    }
  }

  private BotResponse handleToggleConfirm(
      String key, Long userId, Integer messageId, Locale locale) {
    Boolean newState = pendingToggleStore.get(key);
    if (newState == null) {
      return BotResponse.edit(
          msg.msg(locale, "flags.toggle.no_pending", key), List.of(), messageId);
    }

    try {
      String actorName = botUserService.getDisplayName(userId);
      envClient.toggleFlag(key, newState, actorName);
      pendingToggleStore.remove(key);
      log.info("AUDIT flag_toggle: userId={}, actor={}, flagKey={}, newState={}", userId, actorName, key, newState);

      String icon =
          newState
              ? "\uD83D\uDFE2 " + msg.msg(locale, "state.on")
              : "\uD83D\uDD34 " + msg.msg(locale, "state.off");
      String text = "\u2705 " + msg.msg(locale, "flags.toggle.success", key, icon);

      var kb =
          InlineKeyboardBuilder.create()
              .button(
                  "\uD83D\uDEA9 " + msg.msg(locale, "flags.view.title", key), "flags:view:" + key)
              .row()
              .navRow(
                  "\u2B05\uFE0F " + msg.msg(locale, "common.btn.back"),
                  "flags:list:0",
                  "\uD83C\uDFE0 " + msg.msg(locale, "common.btn.home"),
                  "nav:home");

      return BotResponse.edit(text, kb.build(), messageId);
    } catch (Exception e) {
      log.error("Failed to toggle flag '{}': {}", key, e.getMessage());
      pendingToggleStore.remove(key);
      return BotResponse.edit(msg.msg(locale, "flags.toggle.error", key), List.of(), messageId);
    }
  }

  private BotResponse handleToggleCancel(String key, Integer messageId, Locale locale) {
    pendingToggleStore.remove(key);
    String text = "\u274C " + msg.msg(locale, "flags.toggle.cancelled", key);

    var kb =
        InlineKeyboardBuilder.create()
            .button("\uD83D\uDEA9 " + msg.msg(locale, "flags.view.title", key), "flags:view:" + key)
            .row()
            .navRow(
                "\u2B05\uFE0F " + msg.msg(locale, "common.btn.back"),
                "flags:list:0",
                "\uD83C\uDFE0 " + msg.msg(locale, "common.btn.home"),
                "nav:home");

    return BotResponse.edit(text, kb.build(), messageId);
  }
}
