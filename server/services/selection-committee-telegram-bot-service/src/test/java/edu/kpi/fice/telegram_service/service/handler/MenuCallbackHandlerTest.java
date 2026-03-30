package edu.kpi.fice.telegram_service.service.handler;

import static org.assertj.core.api.Assertions.assertThat;

import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;

@ExtendWith(MockitoExtension.class)
@DisplayName("MenuCallbackHandler")
class MenuCallbackHandlerTest {

  private static final Locale LOCALE = Locale.ENGLISH;

  private MenuCallbackHandler handler;

  @BeforeEach
  void setUp() {
    var source = new ResourceBundleMessageSource();
    source.setBasename("messages/messages");
    source.setDefaultEncoding("UTF-8");
    source.setUseCodeAsDefaultMessage(true);
    BotMessageResolver msg = new BotMessageResolver(source);
    handler = new MenuCallbackHandler(msg);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // supports
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("supports")
  class Supports {

    @Test
    @DisplayName("returns true for 'menu' module")
    void supportsMenu() {
      assertThat(handler.supports("menu")).isTrue();
    }

    @Test
    @DisplayName("returns true for 'nav' module")
    void supportsNav() {
      assertThat(handler.supports("nav")).isTrue();
    }

    @Test
    @DisplayName("returns false for 'flags'")
    void doesNotSupportFlags() {
      assertThat(handler.supports("flags")).isFalse();
    }

    @Test
    @DisplayName("returns false for 'settings'")
    void doesNotSupportSettings() {
      assertThat(handler.supports("settings")).isFalse();
    }

    @Test
    @DisplayName("returns false for empty string")
    void doesNotSupportEmpty() {
      assertThat(handler.supports("")).isFalse();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // buildMainMenu
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("buildMainMenu")
  class BuildMainMenu {

    @Test
    @DisplayName("returns withKeyboard response when editMessageId is null")
    void buildMainMenu_returnsWithKeyboard_whenEditMessageIdIsNull() {
      BotResponse result = handler.buildMainMenu(LOCALE, null);

      assertThat(result.isEdit()).isFalse();
      assertThat(result.hasKeyboard()).isTrue();
    }

    @Test
    @DisplayName("returns edit response when editMessageId is provided")
    void buildMainMenu_returnsEdit_whenEditMessageIdProvided() {
      BotResponse result = handler.buildMainMenu(LOCALE, 42);

      assertThat(result.isEdit()).isTrue();
      assertThat(result.editMessageId()).isEqualTo(42);
    }

    @Test
    @DisplayName("keyboard contains four rows for the main menu sections")
    void buildMainMenu_keyboardContainsFourRows() {
      BotResponse result = handler.buildMainMenu(LOCALE, null);

      List<List<edu.kpi.fice.telegram_service.dto.BotResponse.InlineButton>> keyboard =
          result.keyboard();
      assertThat(keyboard).hasSize(4);
    }

    @Test
    @DisplayName(
        "keyboard rows contain expected callback data for flags, health, notifications, settings")
    void buildMainMenu_keyboardRowsContainExpectedCallbackData() {
      BotResponse result = handler.buildMainMenu(LOCALE, null);

      List<List<edu.kpi.fice.telegram_service.dto.BotResponse.InlineButton>> keyboard =
          result.keyboard();

      assertThat(keyboard.get(0)).anyMatch(b -> b.callbackData().equals("flags:list:0"));
      assertThat(keyboard.get(1)).anyMatch(b -> b.callbackData().equals("health:services"));
      assertThat(keyboard.get(2)).anyMatch(b -> b.callbackData().equals("notif:list"));
      assertThat(keyboard.get(3)).anyMatch(b -> b.callbackData().equals("settings:main"));
    }

    @Test
    @DisplayName("menu title text is bold HTML")
    void buildMainMenu_textContainsBoldTitle() {
      BotResponse result = handler.buildMainMenu(LOCALE, null);

      assertThat(result.text()).startsWith("<b>");
      assertThat(result.text()).contains("Main Menu");
    }

    @Test
    @DisplayName("handle delegates to buildMainMenu")
    void handle_delegatesToBuildMainMenu() {
      BotResponse result = handler.handle("menu:main", 1L, 10L, 5, LOCALE);

      assertThat(result.isEdit()).isTrue();
      assertThat(result.editMessageId()).isEqualTo(5);
    }
  }
}
