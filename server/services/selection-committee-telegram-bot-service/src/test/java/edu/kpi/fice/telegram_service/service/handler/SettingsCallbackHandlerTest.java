package edu.kpi.fice.telegram_service.service.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.kpi.fice.telegram_service.domain.BotUser;
import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import edu.kpi.fice.telegram_service.service.BotUserService;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettingsCallbackHandler")
class SettingsCallbackHandlerTest {

  private static final Locale LOCALE = Locale.ENGLISH;

  @Mock private BotUserService botUserService;

  private SettingsCallbackHandler handler;

  @BeforeEach
  void setUp() {
    var source = new ResourceBundleMessageSource();
    source.setBasename("messages/messages");
    source.setDefaultEncoding("UTF-8");
    source.setUseCodeAsDefaultMessage(true);
    BotMessageResolver msg = new BotMessageResolver(source);
    handler = new SettingsCallbackHandler(msg, botUserService);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // supports
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("supports")
  class Supports {

    @Test
    @DisplayName("returns true for 'settings' module")
    void supportsSettings() {
      assertThat(handler.supports("settings")).isTrue();
    }

    @Test
    @DisplayName("returns false for 'menu'")
    void doesNotSupportMenu() {
      assertThat(handler.supports("menu")).isFalse();
    }

    @Test
    @DisplayName("returns false for 'flags'")
    void doesNotSupportFlags() {
      assertThat(handler.supports("flags")).isFalse();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // handle — main settings menu (default action)
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handle — settings main menu")
  class HandleMainMenu {

    @Test
    @DisplayName("renders settings menu when action is 'main'")
    void handle_rendersSettingsMenu_whenActionIsMain() {
      BotResponse result = handler.handle("settings:main", 1L, 10L, 5, LOCALE);

      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("Settings");
    }

    @Test
    @DisplayName("renders settings menu when callback data has no action part")
    void handle_rendersSettingsMenu_whenNoActionPart() {
      BotResponse result = handler.handle("settings", 1L, 10L, 5, LOCALE);

      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("Settings");
    }

    @Test
    @DisplayName("settings menu keyboard contains language and profile buttons")
    void handle_settingsMenuKeyboard_containsLanguageAndProfile() {
      BotResponse result = handler.handle("settings:main", 1L, 10L, 5, LOCALE);

      assertThat(result.keyboard())
          .anyMatch(row -> row.stream().anyMatch(b -> b.callbackData().equals("settings:lang")));
      assertThat(result.keyboard())
          .anyMatch(row -> row.stream().anyMatch(b -> b.callbackData().equals("settings:profile")));
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // handle — language menu
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handle — language menu")
  class HandleLanguageMenu {

    @Test
    @DisplayName("renders language selection menu when action is 'lang'")
    void handle_rendersLanguageMenu_whenActionIsLang() {
      BotResponse result = handler.handle("settings:lang", 1L, 10L, 5, LOCALE);

      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("Language");
    }

    @Test
    @DisplayName("language menu keyboard contains EN and UK buttons")
    void handle_languageMenuKeyboard_containsEnAndUkButtons() {
      BotResponse result = handler.handle("settings:lang", 1L, 10L, 5, LOCALE);

      assertThat(result.keyboard())
          .anyMatch(row -> row.stream().anyMatch(b -> b.callbackData().equals("settings:lang_en")));
      assertThat(result.keyboard())
          .anyMatch(row -> row.stream().anyMatch(b -> b.callbackData().equals("settings:lang_uk")));
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // handle — language change
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handle — language change")
  class HandleLanguageChange {

    @Test
    @DisplayName("calls updateLanguage with 'en' when action is lang_en")
    void handle_updatesLanguageToEn_whenActionIsLangEn() {
      BotUser user = BotUser.builder().telegramUserId(1L).chatId(10L).languageCode("en").build();
      when(botUserService.updateLanguage(1L, "en")).thenReturn(user);

      BotResponse result = handler.handle("settings:lang_en", 1L, 10L, 5, LOCALE);

      verify(botUserService).updateLanguage(1L, "en");
      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("Language changed");
    }

    @Test
    @DisplayName("calls updateLanguage with 'uk' when action is lang_uk")
    void handle_updatesLanguageToUk_whenActionIsLangUk() {
      BotUser user = BotUser.builder().telegramUserId(1L).chatId(10L).languageCode("uk").build();
      when(botUserService.updateLanguage(1L, "uk")).thenReturn(user);

      BotResponse result = handler.handle("settings:lang_uk", 1L, 10L, 5, LOCALE);

      verify(botUserService).updateLanguage(1L, "uk");
      assertThat(result.isEdit()).isTrue();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // handle — profile
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handle — profile")
  class HandleProfile {

    @Test
    @DisplayName("renders profile view when action is 'profile'")
    void handle_rendersProfile_whenActionIsProfile() {
      var user =
          edu.kpi.fice.telegram_service.domain.BotUser.builder()
              .telegramUserId(1L)
              .chatId(10L)
              .role("admin")
              .languageCode("en")
              .build();
      when(botUserService.findUser(1L)).thenReturn(java.util.Optional.of(user));

      BotResponse result = handler.handle("settings:profile", 1L, 10L, 5, LOCALE);

      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("Profile");
      assertThat(result.text()).contains("admin");
      verify(botUserService).findUser(1L);
    }
  }
}
