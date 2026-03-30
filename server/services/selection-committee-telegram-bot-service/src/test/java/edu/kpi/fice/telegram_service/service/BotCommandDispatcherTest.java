package edu.kpi.fice.telegram_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import edu.kpi.fice.telegram_service.service.handler.BotCommandHandler;
import edu.kpi.fice.telegram_service.service.handler.FlagsCallbackHandler;
import edu.kpi.fice.telegram_service.service.handler.MenuCallbackHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;

@ExtendWith(MockitoExtension.class)
@DisplayName("BotCommandDispatcher")
class BotCommandDispatcherTest {

  private static final String HELP_HEADER = "<b>Bot Commands</b>";
  private static final Locale LOCALE = Locale.ENGLISH;

  @Mock private BotCommandHandler flagsHandler;
  @Mock private MenuCallbackHandler menuHandler;
  @Mock private FlagsCallbackHandler flagsCallbackHandler;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  private BotMessageResolver createRealMessageResolver() {
    var source = new ResourceBundleMessageSource();
    source.setBasename("messages/messages");
    source.setDefaultEncoding("UTF-8");
    source.setUseCodeAsDefaultMessage(true);
    return new BotMessageResolver(source);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // /help and /start
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("help and start commands")
  class HelpAndStart {

    @Test
    @DisplayName("/help returns the help text")
    void helpCommand_returnsHelpText() {
      BotCommandDispatcher dispatcher =
          new BotCommandDispatcher(
              List.of(flagsHandler),
              menuHandler,
              flagsCallbackHandler,
              createRealMessageResolver(),
              meterRegistry);

      BotResponse result = dispatcher.dispatch("/help", null, null, LOCALE, null);

      assertThat(result.text()).contains(HELP_HEADER);
      assertThat(result.text()).contains("/flags");
      assertThat(result.text()).contains("/flag");
      assertThat(result.text()).contains("/search");
    }

    @Test
    @DisplayName("/start delegates to menuHandler.buildMainMenu")
    void startCommand_delegatesToMenuHandler() {
      when(menuHandler.buildMainMenu(LOCALE, null)).thenReturn(BotResponse.text("Main Menu"));
      BotCommandDispatcher dispatcher =
          new BotCommandDispatcher(
              List.of(flagsHandler),
              menuHandler,
              flagsCallbackHandler,
              createRealMessageResolver(),
              meterRegistry);

      BotResponse result = dispatcher.dispatch("/start", null, null, LOCALE, null);

      assertThat(result.text()).isEqualTo("Main Menu");
      verify(menuHandler).buildMainMenu(LOCALE, null);
    }

    @Test
    @DisplayName("/help does not delegate to any handler")
    void helpCommand_doesNotDelegateToHandlers() {
      BotCommandDispatcher dispatcher =
          new BotCommandDispatcher(
              List.of(flagsHandler),
              menuHandler,
              flagsCallbackHandler,
              createRealMessageResolver(),
              meterRegistry);

      dispatcher.dispatch("/help", null, null, LOCALE, null);

      verify(flagsHandler, never()).supports("/help");
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // null and blank input
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("null and blank input")
  class NullAndBlankInput {

    @Test
    @DisplayName("null text returns help text")
    void nullText_returnsHelpText() {
      BotCommandDispatcher dispatcher =
          new BotCommandDispatcher(
              List.of(flagsHandler),
              menuHandler,
              flagsCallbackHandler,
              createRealMessageResolver(),
              meterRegistry);

      BotResponse result = dispatcher.dispatch(null, null, null, LOCALE, null);

      assertThat(result.text()).contains(HELP_HEADER);
    }

    @Test
    @DisplayName("blank text returns help text")
    void blankText_returnsHelpText() {
      BotCommandDispatcher dispatcher =
          new BotCommandDispatcher(
              List.of(flagsHandler),
              menuHandler,
              flagsCallbackHandler,
              createRealMessageResolver(),
              meterRegistry);

      BotResponse result = dispatcher.dispatch("   ", null, null, LOCALE, null);

      assertThat(result.text()).contains(HELP_HEADER);
    }

    @Test
    @DisplayName("empty string returns help text")
    void emptyString_returnsHelpText() {
      BotCommandDispatcher dispatcher =
          new BotCommandDispatcher(
              List.of(flagsHandler),
              menuHandler,
              flagsCallbackHandler,
              createRealMessageResolver(),
              meterRegistry);

      BotResponse result = dispatcher.dispatch("", null, null, LOCALE, null);

      assertThat(result.text()).contains(HELP_HEADER);
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // delegation to handlers
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handler delegation")
  class HandlerDelegation {

    @Test
    @DisplayName("/flags delegates to the handler that supports it")
    void flagsCommand_delegatesToMatchingHandler() {
      when(flagsHandler.supports("/flags")).thenReturn(true);
      when(flagsHandler.handle(new String[0], LOCALE)).thenReturn(BotResponse.text("flag list"));
      BotCommandDispatcher dispatcher =
          new BotCommandDispatcher(
              List.of(flagsHandler),
              menuHandler,
              flagsCallbackHandler,
              createRealMessageResolver(),
              meterRegistry);

      BotResponse result = dispatcher.dispatch("/flags", null, null, LOCALE, null);

      assertThat(result.text()).isEqualTo("flag list");
      verify(flagsHandler).handle(new String[0], LOCALE);
    }

    @Test
    @DisplayName("/flags with args passes the args array to the handler")
    void flagsCommandWithArgs_passesArgsToHandler() {
      when(flagsHandler.supports("/flag")).thenReturn(true);
      when(flagsHandler.handle(new String[] {"dark-mode"}, LOCALE))
          .thenReturn(BotResponse.text("details"));
      BotCommandDispatcher dispatcher =
          new BotCommandDispatcher(
              List.of(flagsHandler),
              menuHandler,
              flagsCallbackHandler,
              createRealMessageResolver(),
              meterRegistry);

      BotResponse result = dispatcher.dispatch("/flag dark-mode", null, null, LOCALE, null);

      assertThat(result.text()).isEqualTo("details");
      verify(flagsHandler).handle(new String[] {"dark-mode"}, LOCALE);
    }

    @Test
    @DisplayName("first matching handler is used when multiple handlers exist")
    void multipleHandlers_firstMatchingHandlerIsUsed() {
      BotCommandHandler secondHandler = org.mockito.Mockito.mock(BotCommandHandler.class);
      when(flagsHandler.supports("/flags")).thenReturn(true);
      when(flagsHandler.handle(new String[0], LOCALE)).thenReturn(BotResponse.text("first"));
      BotCommandDispatcher dispatcher =
          new BotCommandDispatcher(
              List.of(flagsHandler, secondHandler),
              menuHandler,
              flagsCallbackHandler,
              createRealMessageResolver(),
              meterRegistry);

      BotResponse result = dispatcher.dispatch("/flags", null, null, LOCALE, null);

      assertThat(result.text()).isEqualTo("first");
      verify(secondHandler, never()).handle(any(), any());
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // unknown command
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("unknown command")
  class UnknownCommand {

    @Test
    @DisplayName("unknown command returns an error message containing the command")
    void unknownCommand_returnsErrorMessage() {
      when(flagsHandler.supports("/unknown")).thenReturn(false);
      BotCommandDispatcher dispatcher =
          new BotCommandDispatcher(
              List.of(flagsHandler),
              menuHandler,
              flagsCallbackHandler,
              createRealMessageResolver(),
              meterRegistry);

      BotResponse result = dispatcher.dispatch("/unknown", null, null, LOCALE, null);

      assertThat(result.text()).contains("Unknown command");
      assertThat(result.text()).contains("/unknown");
      assertThat(result.text()).contains("/help");
    }

    @Test
    @DisplayName("unknown command with no handlers at all returns an error message")
    void unknownCommand_noHandlers_returnsErrorMessage() {
      BotCommandDispatcher dispatcher =
          new BotCommandDispatcher(
              List.of(),
              menuHandler,
              flagsCallbackHandler,
              createRealMessageResolver(),
              meterRegistry);

      BotResponse result = dispatcher.dispatch("/notacommand", null, null, LOCALE, null);

      assertThat(result.text()).contains("Unknown command");
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // bot username suffix stripping
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("bot username suffix stripping")
  class BotUsernameSuffix {

    @Test
    @DisplayName("/flags@MyBot is stripped to /flags before matching handlers")
    void commandWithBotUsernameSuffix_isStrippedBeforeDispatch() {
      when(flagsHandler.supports("/flags")).thenReturn(true);
      when(flagsHandler.handle(new String[0], LOCALE)).thenReturn(BotResponse.text("flag list"));
      BotCommandDispatcher dispatcher =
          new BotCommandDispatcher(
              List.of(flagsHandler),
              menuHandler,
              flagsCallbackHandler,
              createRealMessageResolver(),
              meterRegistry);

      BotResponse result = dispatcher.dispatch("/flags@MyBot", null, null, LOCALE, null);

      assertThat(result.text()).isEqualTo("flag list");
      verify(flagsHandler).supports("/flags");
    }

    @Test
    @DisplayName("/help@AnyBot still returns help text without delegating")
    void helpWithBotSuffix_returnsHelpTextWithoutDelegating() {
      BotCommandDispatcher dispatcher =
          new BotCommandDispatcher(
              List.of(flagsHandler),
              menuHandler,
              flagsCallbackHandler,
              createRealMessageResolver(),
              meterRegistry);

      BotResponse result = dispatcher.dispatch("/help@SomeBot", null, null, LOCALE, null);

      assertThat(result.text()).contains(HELP_HEADER);
      verify(flagsHandler, never()).supports(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("/start@AnyBot still delegates to menuHandler")
    void startWithBotSuffix_delegatesToMenuHandler() {
      when(menuHandler.buildMainMenu(LOCALE, null)).thenReturn(BotResponse.text("Main Menu"));
      BotCommandDispatcher dispatcher =
          new BotCommandDispatcher(
              List.of(flagsHandler),
              menuHandler,
              flagsCallbackHandler,
              createRealMessageResolver(),
              meterRegistry);

      BotResponse result = dispatcher.dispatch("/start@SomeBot", null, null, LOCALE, null);

      assertThat(result.text()).isEqualTo("Main Menu");
    }

    @Test
    @DisplayName("/start flag_dark-mode deep link shows flag detail")
    void startWithDeepLink_showsFlagDetail() {
      when(flagsCallbackHandler.handle("flags:view:dark-mode", null, null, null, LOCALE))
          .thenReturn(BotResponse.text("Dark Mode Flag"));
      BotCommandDispatcher dispatcher =
          new BotCommandDispatcher(
              List.of(flagsHandler),
              menuHandler,
              flagsCallbackHandler,
              createRealMessageResolver(),
              meterRegistry);

      BotResponse result = dispatcher.dispatch("/start flag_dark-mode", null, null, LOCALE, null);

      assertThat(result.text()).isEqualTo("Dark Mode Flag");
    }

    @Test
    @DisplayName("unknown command with bot suffix reports stripped command in error message")
    void unknownCommandWithBotSuffix_reportsStrippedCommandInError() {
      when(flagsHandler.supports("/unknown")).thenReturn(false);
      BotCommandDispatcher dispatcher =
          new BotCommandDispatcher(
              List.of(flagsHandler),
              menuHandler,
              flagsCallbackHandler,
              createRealMessageResolver(),
              meterRegistry);

      BotResponse result = dispatcher.dispatch("/unknown@SomeBot", null, null, LOCALE, null);

      assertThat(result.text()).contains("/unknown");
      assertThat(result.text()).doesNotContain("@SomeBot");
    }
  }
}
