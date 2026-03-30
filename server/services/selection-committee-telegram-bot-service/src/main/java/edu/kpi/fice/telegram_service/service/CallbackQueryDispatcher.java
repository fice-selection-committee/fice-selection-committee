package edu.kpi.fice.telegram_service.service;

import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import edu.kpi.fice.telegram_service.service.handler.CallbackHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackQueryDispatcher {

  private final List<CallbackHandler> handlers;
  private final BotMessageResolver messageResolver;
  private final MeterRegistry meterRegistry;
  private final NavigationStateManager navigationState;

  /**
   * Dispatches a callback query to the appropriate handler.
   *
   * @param callbackData the callback data (format: "module:action:params")
   * @param userId the Telegram user ID
   * @param chatId the chat ID
   * @param messageId the message ID to edit
   * @param locale the user's locale
   * @return a BotResponse
   */
  public BotResponse dispatch(
      String callbackData, Long userId, Long chatId, Integer messageId, Locale locale) {
    if (callbackData == null || callbackData.isBlank()) {
      return BotResponse.text(messageResolver.msg(locale, "common.error.generic"));
    }

    // Handle nav:back by popping the navigation stack
    if ("nav:back".equals(callbackData)) {
      Optional<String> previous = navigationState.pop(chatId);
      if (previous.isPresent()) {
        return dispatch(previous.get(), userId, chatId, messageId, locale);
      }
      // Fall through to home if no history
      callbackData = "nav:home";
    }

    // Handle nav:home by clearing the stack
    if ("nav:home".equals(callbackData)) {
      navigationState.reset(chatId);
    }

    // Track navigation state
    if (!callbackData.startsWith("nav:")) {
      navigationState.push(chatId, callbackData);
    }

    String module = callbackData.contains(":") ? callbackData.split(":")[0] : callbackData;

    for (CallbackHandler handler : handlers) {
      if (handler.supports(module)) {
        log.info(
            "Dispatching callback '{}' to {}", callbackData, handler.getClass().getSimpleName());
        Counter.builder("bot.callbacks.total")
            .tag("action", module)
            .register(meterRegistry)
            .increment();
        return handler.handle(callbackData, userId, chatId, messageId, locale);
      }
    }

    log.warn("No handler found for callback data: {}", callbackData);
    return BotResponse.text(messageResolver.msg(locale, "common.error.generic"));
  }
}
