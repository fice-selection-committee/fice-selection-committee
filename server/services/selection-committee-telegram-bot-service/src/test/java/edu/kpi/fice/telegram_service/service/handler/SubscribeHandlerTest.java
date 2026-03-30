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
@DisplayName("SubscribeHandler")
class SubscribeHandlerTest {

  private static final Locale LOCALE = Locale.ENGLISH;

  @Mock private NotificationChatRegistry chatRegistry;
  @Mock private BotUserService botUserService;

  private SubscribeHandler handler;

  @BeforeEach
  void setUp() {
    var source = new ResourceBundleMessageSource();
    source.setBasename("messages/messages");
    source.setDefaultEncoding("UTF-8");
    source.setUseCodeAsDefaultMessage(true);
    BotMessageResolver msg = new BotMessageResolver(source);
    handler = new SubscribeHandler(chatRegistry, botUserService, msg);
  }

  @Nested
  @DisplayName("supports")
  class Supports {

    @Test
    @DisplayName("returns true for /subscribe")
    void supportsSubscribe() {
      assertThat(handler.supports("/subscribe")).isTrue();
    }

    @Test
    @DisplayName("returns false for /unsubscribe")
    void doesNotSupportUnsubscribe() {
      assertThat(handler.supports("/unsubscribe")).isFalse();
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
    @DisplayName("returns already-subscribed message when user is already subscribed via DB")
    void handle_returnsAlreadySubscribed_whenUserIsSubscribed() {
      when(botUserService.isSubscribed(42L)).thenReturn(true);

      BotResponse result = handler.handle(new String[] {"100", "42", ""}, LOCALE);

      assertThat(result.text()).contains("already subscribed");
      verify(botUserService, never()).toggleSubscription(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("subscribes user via DB and chat via registry when not subscribed")
    void handle_subscribesUser_whenNotSubscribed() {
      when(botUserService.isSubscribed(77L)).thenReturn(false);

      BotResponse result = handler.handle(new String[] {"200", "77", "3224"}, LOCALE);

      assertThat(result.text()).contains("Subscribed");
      verify(botUserService).toggleSubscription(77L);
      verify(chatRegistry).subscribe(200L, 3224);
    }

    @Test
    @DisplayName("subscribes with null thread ID when not in a topic")
    void handle_subscribesWithNullThreadId_whenNoTopic() {
      when(botUserService.isSubscribed(77L)).thenReturn(false);

      BotResponse result = handler.handle(new String[] {"200", "77", ""}, LOCALE);

      assertThat(result.text()).contains("Subscribed");
      verify(botUserService).toggleSubscription(77L);
      verify(chatRegistry).subscribe(200L, null);
    }

    @Test
    @DisplayName("returns plain text response without keyboard")
    void handle_returnsTextOnlyResponse() {
      when(botUserService.isSubscribed(55L)).thenReturn(false);

      BotResponse result = handler.handle(new String[] {"300", "55", ""}, LOCALE);

      assertThat(result.hasKeyboard()).isFalse();
      assertThat(result.isEdit()).isFalse();
    }
  }
}
