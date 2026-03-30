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
@DisplayName("NotificationsCallbackHandler")
class NotificationsCallbackHandlerTest {

  private static final Locale LOCALE = Locale.ENGLISH;
  private static final Long USER_ID = 1L;
  private static final Long CHAT_ID = 10L;
  private static final Integer MESSAGE_ID = 42;

  @Mock private BotUserService botUserService;

  private NotificationsCallbackHandler handler;

  @BeforeEach
  void setUp() {
    var source = new ResourceBundleMessageSource();
    source.setBasename("messages/messages");
    source.setDefaultEncoding("UTF-8");
    source.setUseCodeAsDefaultMessage(true);
    BotMessageResolver msg = new BotMessageResolver(source);
    handler = new NotificationsCallbackHandler(msg, botUserService);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // supports
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("supports")
  class Supports {

    @Test
    @DisplayName("returns true for 'notif' module")
    void supportsNotif() {
      assertThat(handler.supports("notif")).isTrue();
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

    @Test
    @DisplayName("returns false for 'notifications' (full word)")
    void doesNotSupportFullWord() {
      assertThat(handler.supports("notifications")).isFalse();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // handle — list
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handle — list")
  class HandleList {

    @Test
    @DisplayName("renders subscribed status when user is subscribed")
    void handle_rendersList_showsSubscribedStatus() {
      when(botUserService.isSubscribed(USER_ID)).thenReturn(true);

      BotResponse result = handler.handle("notif:list", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("subscribed");
      // Should show unsubscribe button
      assertThat(result.keyboard())
          .anyMatch(
              row ->
                  row.stream()
                      .anyMatch(
                          b ->
                              b.callbackData().equals("notif:toggle")
                                  && b.text().contains("Unsubscribe")));
    }

    @Test
    @DisplayName("renders not-subscribed status when user is not subscribed")
    void handle_rendersList_showsNotSubscribedStatus() {
      when(botUserService.isSubscribed(USER_ID)).thenReturn(false);

      BotResponse result = handler.handle("notif:list", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("not subscribed");
      // Should show subscribe button
      assertThat(result.keyboard())
          .anyMatch(
              row ->
                  row.stream()
                      .anyMatch(
                          b ->
                              b.callbackData().equals("notif:toggle")
                                  && b.text().contains("Subscribe")));
    }

    @Test
    @DisplayName("renders notification list view when no action is specified")
    void handle_rendersList_whenNoAction() {
      when(botUserService.isSubscribed(USER_ID)).thenReturn(false);

      BotResponse result = handler.handle("notif", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("Notifications");
    }

    @Test
    @DisplayName("list view keyboard contains toggle subscription button")
    void handle_listKeyboard_containsToggleButton() {
      when(botUserService.isSubscribed(USER_ID)).thenReturn(false);

      BotResponse result = handler.handle("notif:list", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.keyboard())
          .anyMatch(row -> row.stream().anyMatch(b -> b.callbackData().equals("notif:toggle")));
    }

    @Test
    @DisplayName("list view keyboard contains nav row with back and home buttons")
    void handle_listKeyboard_containsNavRow() {
      BotResponse result = handler.handle("notif:list", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.keyboard())
          .anyMatch(row -> row.stream().anyMatch(b -> b.callbackData().equals("menu:main")));
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // handle — toggle
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handle — toggle")
  class HandleToggle {

    @Test
    @DisplayName("calls toggleSubscription and renders subscribed success when user subscribes")
    void handle_toggle_rendersSubscribed_whenUserSubscribes() {
      BotUser subscribedUser =
          BotUser.builder().telegramUserId(USER_ID).chatId(CHAT_ID).subscribed(true).build();
      when(botUserService.toggleSubscription(USER_ID)).thenReturn(subscribedUser);

      BotResponse result = handler.handle("notif:toggle", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      verify(botUserService).toggleSubscription(USER_ID);
      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("Subscribed");
    }

    @Test
    @DisplayName("calls toggleSubscription and renders unsubscribed success when user unsubscribes")
    void handle_toggle_rendersUnsubscribed_whenUserUnsubscribes() {
      BotUser unsubscribedUser =
          BotUser.builder().telegramUserId(USER_ID).chatId(CHAT_ID).subscribed(false).build();
      when(botUserService.toggleSubscription(USER_ID)).thenReturn(unsubscribedUser);

      BotResponse result = handler.handle("notif:toggle", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      verify(botUserService).toggleSubscription(USER_ID);
      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("Unsubscribed");
    }

    @Test
    @DisplayName("toggle response keyboard contains nav row")
    void handle_toggle_responseKeyboard_containsNavRow() {
      BotUser user =
          BotUser.builder().telegramUserId(USER_ID).chatId(CHAT_ID).subscribed(true).build();
      when(botUserService.toggleSubscription(USER_ID)).thenReturn(user);

      BotResponse result = handler.handle("notif:toggle", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.keyboard())
          .anyMatch(row -> row.stream().anyMatch(b -> b.callbackData().equals("notif:list")));
    }
  }
}
