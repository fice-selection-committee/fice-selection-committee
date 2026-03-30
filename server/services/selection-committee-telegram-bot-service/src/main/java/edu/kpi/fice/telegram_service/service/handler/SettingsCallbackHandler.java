package edu.kpi.fice.telegram_service.service.handler;

import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import edu.kpi.fice.telegram_service.keyboard.InlineKeyboardBuilder;
import edu.kpi.fice.telegram_service.service.BotUserService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Handles settings menu: language switching and profile view. */
@Component
@RequiredArgsConstructor
public class SettingsCallbackHandler implements CallbackHandler {

  private final BotMessageResolver msg;
  private final BotUserService botUserService;

  @Override
  public boolean supports(String module) {
    return "settings".equals(module);
  }

  @Override
  public BotResponse handle(
      String callbackData, Long userId, Long chatId, Integer messageId, Locale locale) {
    String[] parts = callbackData.split(":");
    String action = parts.length > 1 ? parts[1] : "main";

    return switch (action) {
      case "lang" -> handleLanguageMenu(locale, messageId);
      case "lang_en" -> handleLanguageChange(userId, "en", messageId);
      case "lang_uk" -> handleLanguageChange(userId, "uk", messageId);
      case "profile" -> handleProfile(userId, locale, messageId);
      default -> handleSettingsMenu(locale, messageId);
    };
  }

  private BotResponse handleSettingsMenu(Locale locale, Integer messageId) {
    String text = "<b>\u2699\uFE0F " + msg.msg(locale, "settings.title") + "</b>";

    var keyboard =
        InlineKeyboardBuilder.create()
            .button("\uD83C\uDF10 " + msg.msg(locale, "settings.language.title"), "settings:lang")
            .row()
            .button("\uD83D\uDC64 " + msg.msg(locale, "settings.profile.title"), "settings:profile")
            .row()
            .navRow(
                "\u2B05\uFE0F " + msg.msg(locale, "common.btn.back"),
                "menu:main",
                "\uD83C\uDFE0 " + msg.msg(locale, "common.btn.home"),
                "nav:home")
            .build();

    return BotResponse.edit(text, keyboard, messageId);
  }

  private BotResponse handleLanguageMenu(Locale locale, Integer messageId) {
    String text =
        "<b>\uD83C\uDF10 "
            + msg.msg(locale, "settings.language.title")
            + "</b>\n\n"
            + msg.msg(locale, "settings.language.current", locale.getDisplayLanguage(locale));

    var keyboard =
        InlineKeyboardBuilder.create()
            .button(msg.msg(locale, "settings.language.btn.en"), "settings:lang_en")
            .button(msg.msg(locale, "settings.language.btn.uk"), "settings:lang_uk")
            .row()
            .navRow(
                "\u2B05\uFE0F " + msg.msg(locale, "common.btn.back"),
                "settings:main",
                "\uD83C\uDFE0 " + msg.msg(locale, "common.btn.home"),
                "nav:home")
            .build();

    return BotResponse.edit(text, keyboard, messageId);
  }

  private BotResponse handleLanguageChange(Long userId, String langCode, Integer messageId) {
    var user = botUserService.updateLanguage(userId, langCode);
    Locale newLocale = BotMessageResolver.resolveLocale(langCode);

    String langName = newLocale.getDisplayLanguage(newLocale);
    String text = "\u2705 " + msg.msg(newLocale, "settings.language.changed", langName);

    var keyboard =
        InlineKeyboardBuilder.create()
            .navRow(
                "\u2B05\uFE0F " + msg.msg(newLocale, "common.btn.back"),
                "settings:main",
                "\uD83C\uDFE0 " + msg.msg(newLocale, "common.btn.home"),
                "nav:home")
            .build();

    return BotResponse.edit(text, keyboard, messageId);
  }

  private BotResponse handleProfile(Long userId, Locale locale, Integer messageId) {
    var user = botUserService.findUser(userId).orElse(null);
    String role = user != null ? user.getRole() : "unknown";
    String language = user != null ? user.getLanguageCode() : "en";

    String text =
        "<b>\uD83D\uDC64 "
            + msg.msg(locale, "settings.profile.title")
            + "</b>\n\n"
            + msg.msg(locale, "settings.profile.role", role)
            + "\n"
            + msg.msg(locale, "settings.profile.language", language);

    var keyboard =
        InlineKeyboardBuilder.create()
            .navRow(
                "\u2B05\uFE0F " + msg.msg(locale, "common.btn.back"),
                "settings:main",
                "\uD83C\uDFE0 " + msg.msg(locale, "common.btn.home"),
                "nav:home")
            .build();

    return BotResponse.edit(text, keyboard, messageId);
  }
}
