package edu.kpi.fice.telegram_service.service.handler;

import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import edu.kpi.fice.telegram_service.keyboard.InlineKeyboardBuilder;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Handles main menu rendering and top-level navigation. */
@Component
@RequiredArgsConstructor
public class MenuCallbackHandler implements CallbackHandler {

  private final BotMessageResolver msg;

  @Override
  public boolean supports(String module) {
    return "menu".equals(module) || "nav".equals(module);
  }

  @Override
  public BotResponse handle(
      String callbackData, Long userId, Long chatId, Integer messageId, Locale locale) {
    // Both "menu:main" and "nav:home" render the main menu
    return buildMainMenu(locale, messageId);
  }

  public BotResponse buildMainMenu(Locale locale, Integer editMessageId) {
    String text = "<b>" + msg.msg(locale, "menu.main.title") + "</b>";

    var keyboard =
        InlineKeyboardBuilder.create()
            .button("\uD83D\uDEA9 " + msg.msg(locale, "menu.main.flags"), "flags:list:0")
            .row()
            .button("\uD83D\uDC9A " + msg.msg(locale, "menu.main.health"), "health:services")
            .row()
            .button("\uD83D\uDD14 " + msg.msg(locale, "menu.main.notifications"), "notif:list")
            .row()
            .button("\u2699\uFE0F " + msg.msg(locale, "menu.main.settings"), "settings:main")
            .row()
            .build();

    if (editMessageId != null) {
      return BotResponse.edit(text, keyboard, editMessageId);
    }
    return BotResponse.withKeyboard(text, keyboard);
  }
}
