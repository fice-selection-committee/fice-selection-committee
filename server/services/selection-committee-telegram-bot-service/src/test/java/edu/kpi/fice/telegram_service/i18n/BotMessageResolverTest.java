package edu.kpi.fice.telegram_service.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

@DisplayName("BotMessageResolver")
class BotMessageResolverTest {

  private BotMessageResolver resolver;

  @BeforeEach
  void setUp() {
    var source = new ResourceBundleMessageSource();
    source.setBasename("messages/messages");
    source.setDefaultEncoding("UTF-8");
    source.setUseCodeAsDefaultMessage(true);
    resolver = new BotMessageResolver(source);
  }

  @Nested
  @DisplayName("msg with locale")
  class MsgWithLocale {

    @Test
    @DisplayName("returns English message for ENGLISH locale")
    void returnsEnglishMessage() {
      String result = resolver.msg(Locale.ENGLISH, "flags.list.empty");

      assertThat(result).isEqualTo("No feature flags found.");
    }

    @Test
    @DisplayName("returns Ukrainian message for UK locale")
    void returnsUkrainianMessage() {
      Locale uk = Locale.forLanguageTag("uk");

      String result = resolver.msg(uk, "flags.list.empty");

      assertThat(result).contains("\u0424\u043B\u0430\u0433\u0456\u0432");
    }

    @Test
    @DisplayName("substitutes arguments into message template")
    void substitutesArguments() {
      String result = resolver.msg(Locale.ENGLISH, "flags.list.total", 42);

      assertThat(result).isEqualTo("Total: 42");
    }

    @Test
    @DisplayName("returns key as default when key is missing")
    void returnKeyWhenMissing() {
      String result = resolver.msg(Locale.ENGLISH, "nonexistent.key");

      assertThat(result).isEqualTo("nonexistent.key");
    }
  }

  @Nested
  @DisplayName("msg without locale (default English)")
  class MsgWithoutLocale {

    @Test
    @DisplayName("uses English locale by default")
    void usesEnglishByDefault() {
      String result = resolver.msg("menu.main.title");

      assertThat(result).isEqualTo("Main Menu");
    }
  }

  @Nested
  @DisplayName("resolveLocale")
  class ResolveLocale {

    @Test
    @DisplayName("returns UK locale for 'uk'")
    void ukCodeReturnsUkLocale() {
      assertThat(BotMessageResolver.resolveLocale("uk")).isEqualTo(BotMessageResolver.LOCALE_UK);
    }

    @Test
    @DisplayName("returns UK locale for 'ua'")
    void uaCodeReturnsUkLocale() {
      assertThat(BotMessageResolver.resolveLocale("ua")).isEqualTo(BotMessageResolver.LOCALE_UK);
    }

    @Test
    @DisplayName("returns English locale for 'en'")
    void enCodeReturnsEnLocale() {
      assertThat(BotMessageResolver.resolveLocale("en")).isEqualTo(BotMessageResolver.LOCALE_EN);
    }

    @Test
    @DisplayName("returns English locale for null")
    void nullReturnsEnLocale() {
      assertThat(BotMessageResolver.resolveLocale(null)).isEqualTo(BotMessageResolver.LOCALE_EN);
    }

    @Test
    @DisplayName("returns English locale for unknown code")
    void unknownCodeReturnsEnLocale() {
      assertThat(BotMessageResolver.resolveLocale("de")).isEqualTo(BotMessageResolver.LOCALE_EN);
    }
  }
}
