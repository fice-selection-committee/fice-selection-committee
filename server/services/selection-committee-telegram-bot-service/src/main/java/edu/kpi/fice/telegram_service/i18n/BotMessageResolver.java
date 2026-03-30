package edu.kpi.fice.telegram_service.i18n;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BotMessageResolver {

  public static final Locale LOCALE_EN = Locale.ENGLISH;
  public static final Locale LOCALE_UK = Locale.forLanguageTag("uk");

  private final MessageSource botMessageSource;

  public String msg(Locale locale, String key, Object... args) {
    return botMessageSource.getMessage(key, args, locale);
  }

  public String msg(String key, Object... args) {
    return msg(LOCALE_EN, key, args);
  }

  public static Locale resolveLocale(String languageCode) {
    if (languageCode == null) {
      return LOCALE_EN;
    }
    return switch (languageCode.toLowerCase()) {
      case "uk", "ua" -> LOCALE_UK;
      default -> LOCALE_EN;
    };
  }
}
