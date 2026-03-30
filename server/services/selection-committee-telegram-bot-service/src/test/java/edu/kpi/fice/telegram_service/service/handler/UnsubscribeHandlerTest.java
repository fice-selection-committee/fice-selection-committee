package edu.kpi.fice.telegram_service.service.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import edu.kpi.fice.telegram_service.service.BotUserService;
import edu.kpi.fice.telegram_service.service.NotificationChatRegistry;
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
@DisplayName("UnsubscribeHandler")
class UnsubscribeHandlerTest {

  private static final Locale LOCALE = Locale.ENGLISH;

  @Mock private NotificationChatRegistry chatRegistry;
  @Mock private BotUserService botUserService;

  private UnsubscribeHandler handler;

  @BeforeEach
  void setUp() {
    var source = new ResourceBundleMessageSource();
    source.setBasename("messages/messages");
    source.setDefaultEncoding("UTF-8");
    source.setUseCodeAsDefaultMessage(true);
    BotMessageResolver msg = new BotMessageResolver(source);
    handler = new UnsubscribeHandler(chatRegistry, botUserService, msg);
  }

  @Nested
  @DisplayName("supports")
  class Supports {

    @Test
    @DisplayName("returns true for /unsubscribe")
    void supportsUnsubscribe() {
      assertThat(handler.supports("/unsubscribe")).isTrue();
    }

    @Test
    @DisplayName("returns false for /subscribe")
    void doesNotSupportSubscribe() {
      assertThat(handler.supports("/subscribe")).isFalse();
    }
  }

  @Nested
  @DisplayName("handle")
  class Handle {

    @Test
    @DisplayName("returns internal error when args are missing")
    void handle_returnsError_whenArgsEmpty() {
      BotResponse result = handler.handle(new String[0], LOCALE);

      assertThat(result.text()).contains("Internal error");
    }

    @Test
    @DisplayName("returns not-subscribed message when user is not subscribed via DB")
    void handle_returnsNotSubscribed_whenUserIsNotSubscribed() {
      when(botUserService.isSubscribed(42L)).thenReturn(false);

      BotResponse result = handler.handle(new String[] {"100", "42", ""}, LOCALE);

      assertThat(result.text()).contains("not subscribed");
      verify(botUserService, never()).toggleSubscription(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("unsubscribes user via DB and chat via registry when subscribed")
    void handle_unsubscribesUser_whenSubscribed() {
      when(botUserService.isSubscribed(77L)).thenReturn(true);

      BotResponse result = handler.handle(new String[] {"200", "77", ""}, LOCALE);

      assertThat(result.text()).contains("Unsubscribed");
      verify(botUserService).toggleSubscription(77L);
      verify(chatRegistry).unsubscribe(200L);
    }

    @Test
    @DisplayName("returns plain text response without keyboard")
    void handle_returnsTextOnlyResponse() {
      when(botUserService.isSubscribed(55L)).thenReturn(true);

      BotResponse result = handler.handle(new String[] {"300", "55", ""}, LOCALE);

      assertThat(result.hasKeyboard()).isFalse();
      assertThat(result.isEdit()).isFalse();
    }
  }
}
