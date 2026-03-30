package edu.kpi.fice.telegram_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import edu.kpi.fice.telegram_service.service.handler.CallbackHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
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
@DisplayName("CallbackQueryDispatcher")
class CallbackQueryDispatcherTest {

  private static final Locale LOCALE = Locale.ENGLISH;
  private static final Long USER_ID = 123L;
  private static final Long CHAT_ID = 456L;
  private static final Integer MSG_ID = 789;

  @Mock private CallbackHandler flagsHandler;
  @Mock private CallbackHandler settingsHandler;

  private CallbackQueryDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    var source = new ResourceBundleMessageSource();
    source.setBasename("messages/messages");
    source.setDefaultEncoding("UTF-8");
    source.setUseCodeAsDefaultMessage(true);
    var msg = new BotMessageResolver(source);

    dispatcher =
        new CallbackQueryDispatcher(
            List.of(flagsHandler, settingsHandler),
            msg,
            new SimpleMeterRegistry(),
            new NavigationStateManager());
  }

  @Nested
  @DisplayName("dispatching")
  class Dispatching {

    @Test
    @DisplayName("dispatches to handler that supports the module prefix")
    void dispatchesToMatchingHandler() {
      when(flagsHandler.supports("flags")).thenReturn(true);
      BotResponse expected = BotResponse.text("flags result");
      when(flagsHandler.handle("flags:list:0", USER_ID, CHAT_ID, MSG_ID, LOCALE))
          .thenReturn(expected);

      BotResponse result = dispatcher.dispatch("flags:list:0", USER_ID, CHAT_ID, MSG_ID, LOCALE);

      assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("dispatches to second handler when first does not support the module")
    void dispatchesToSecondHandler() {
      when(flagsHandler.supports("settings")).thenReturn(false);
      when(settingsHandler.supports("settings")).thenReturn(true);
      BotResponse expected = BotResponse.text("settings result");
      when(settingsHandler.handle("settings:main", USER_ID, CHAT_ID, MSG_ID, LOCALE))
          .thenReturn(expected);

      BotResponse result = dispatcher.dispatch("settings:main", USER_ID, CHAT_ID, MSG_ID, LOCALE);

      assertThat(result).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("error handling")
  class ErrorHandling {

    @Test
    @DisplayName("null callback data returns generic error")
    void nullCallbackData() {
      BotResponse result = dispatcher.dispatch(null, USER_ID, CHAT_ID, MSG_ID, LOCALE);

      assertThat(result.text()).contains("Something went wrong");
    }

    @Test
    @DisplayName("blank callback data returns generic error")
    void blankCallbackData() {
      BotResponse result = dispatcher.dispatch("  ", USER_ID, CHAT_ID, MSG_ID, LOCALE);

      assertThat(result.text()).contains("Something went wrong");
    }

    @Test
    @DisplayName("no matching handler returns generic error")
    void noMatchingHandler() {
      when(flagsHandler.supports("unknown")).thenReturn(false);
      when(settingsHandler.supports("unknown")).thenReturn(false);

      BotResponse result = dispatcher.dispatch("unknown:action", USER_ID, CHAT_ID, MSG_ID, LOCALE);

      assertThat(result.text()).contains("Something went wrong");
    }
  }
}
