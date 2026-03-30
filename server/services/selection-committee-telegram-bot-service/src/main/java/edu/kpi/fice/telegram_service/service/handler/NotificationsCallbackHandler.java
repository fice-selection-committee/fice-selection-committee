package edu.kpi.fice.telegram_service.service.handler;

import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import edu.kpi.fice.telegram_service.keyboard.InlineKeyboardBuilder;
import edu.kpi.fice.telegram_service.service.BotUserService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Handles notification subscription management via inline keyboard. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationsCallbackHandler implements CallbackHandler {

  private final BotMessageResolver msg;
  private final BotUserService botUserService;

  @Override
  public boolean supports(String module) {
    return "notif".equals(module);
  }

  @Override
  public BotResponse handle(
      String callbackData, Long userId, Long chatId, Integer messageId, Locale locale) {
    String[] parts = callbackData.split(":");
    String action = parts.length > 1 ? parts[1] : "list";

    return switch (action) {
      case "toggle" -> handleToggle(userId, messageId, locale);
      default -> handleList(userId, messageId, locale);
    };
  }

  private BotResponse handleList(Long userId, Integer messageId, Locale locale) {
    boolean subscribed = botUserService.isSubscribed(userId);

    var sb =
        new StringBuilder(
            "<b>\uD83D\uDD14 " + msg.msg(locale, "menu.main.notifications") + "</b>\n\n");

    if (subscribed) {
      sb.append("\uD83D\uDD14 ").append(msg.msg(locale, "notif.status.subscribed"));
    } else {
      sb.append("\uD83D\uDD15 ").append(msg.msg(locale, "notif.status.not_subscribed"));
    }

    var kb = InlineKeyboardBuilder.create();

    if (subscribed) {
      kb.button("\uD83D\uDD15 " + msg.msg(locale, "notif.btn.unsubscribe"), "notif:toggle");
    } else {
      kb.button("\uD83D\uDD14 " + msg.msg(locale, "notif.btn.subscribe"), "notif:toggle");
    }
    kb.row()
        .navRow(
            "\u2B05\uFE0F " + msg.msg(locale, "common.btn.back"),
            "menu:main",
            "\uD83C\uDFE0 " + msg.msg(locale, "common.btn.home"),
            "nav:home");

    return BotResponse.edit(sb.toString(), kb.build(), messageId);
  }

  private BotResponse handleToggle(Long userId, Integer messageId, Locale locale) {
    var user = botUserService.toggleSubscription(userId);
    log.info("AUDIT subscription_change: userId={}, subscribed={}", userId, user.getSubscribed());
    String text;
    if (user.getSubscribed()) {
      text = "\u2705 " + msg.msg(locale, "notif.subscribe.success");
    } else {
      text = "\u2705 " + msg.msg(locale, "notif.unsubscribe.success");
    }

    var kb =
        InlineKeyboardBuilder.create()
            .navRow(
                "\u2B05\uFE0F " + msg.msg(locale, "common.btn.back"),
                "notif:list",
                "\uD83C\uDFE0 " + msg.msg(locale, "common.btn.home"),
                "nav:home");

    return BotResponse.edit(text, kb.build(), messageId);
  }
}
