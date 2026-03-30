package edu.kpi.fice.telegram_service.api;

import com.fasterxml.jackson.databind.JsonNode;
import edu.kpi.fice.telegram_service.config.TelegramProperties;
import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.security.RateLimitService;
import edu.kpi.fice.telegram_service.service.BotCommandDispatcher;
import edu.kpi.fice.telegram_service.service.BotUserService;
import edu.kpi.fice.telegram_service.service.CallbackQueryDispatcher;
import edu.kpi.fice.telegram_service.service.TelegramApiClient;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/telegram")
@RequiredArgsConstructor
@ConditionalOnBean(TelegramApiClient.class)
public class TelegramWebhookController {

  private final BotCommandDispatcher dispatcher;
  private final CallbackQueryDispatcher callbackDispatcher;
  private final TelegramApiClient telegramClient;
  private final TelegramProperties telegramProperties;
  private final BotUserService botUserService;
  private final RateLimitService rateLimitService;

  @PostMapping("/webhook")
  public ResponseEntity<Void> handleWebhook(@RequestBody JsonNode update) {
    try {
      // Handle callback queries (inline keyboard button presses)
      JsonNode callbackQuery = update.path("callback_query");
      if (!callbackQuery.isMissingNode()) {
        handleCallbackQuery(callbackQuery);
        return ResponseEntity.ok().build();
      }

      // Handle text messages
      JsonNode message = update.path("message");
      if (message.isMissingNode()) {
        return ResponseEntity.ok().build();
      }

      JsonNode textNode = message.path("text");
      if (textNode.isMissingNode() || textNode.asText().isBlank()) {
        return ResponseEntity.ok().build();
      }

      String text = textNode.asText();
      long chatId = message.path("chat").path("id").asLong();

      if (!telegramProperties.bot().isChatAllowed(chatId)) {
        log.warn("Rejected message from unauthorized chat: {}", chatId);
        return ResponseEntity.ok().build();
      }

      if (rateLimitService.isChatRateLimited(chatId)) {
        return ResponseEntity.ok().build();
      }

      if (!text.startsWith("/")) {
        return ResponseEntity.ok().build();
      }

      // Resolve user for i18n
      long userId = message.path("from").path("id").asLong();
      String languageCode = message.path("from").path("language_code").asText(null);
      String username = message.path("from").path("username").asText(null);
      Integer messageThreadId =
          message.has("message_thread_id") ? message.path("message_thread_id").asInt() : null;
      var botUser =
          botUserService.resolveUser(userId, chatId, languageCode, messageThreadId, username);
      Locale locale =
          edu.kpi.fice.telegram_service.i18n.BotMessageResolver.resolveLocale(
              botUser.getLanguageCode());

      BotResponse response = dispatcher.dispatch(text, chatId, userId, locale, messageThreadId);
      telegramClient.sendResponse(chatId, response, messageThreadId);
    } catch (Exception e) {
      log.error("Error processing Telegram webhook: {}", e.getMessage(), e);
    }

    return ResponseEntity.ok().build();
  }

  private void handleCallbackQuery(JsonNode callbackQuery) {
    String callbackData = callbackQuery.path("data").asText(null);
    String callbackQueryId = callbackQuery.path("id").asText();
    long userId = callbackQuery.path("from").path("id").asLong();
    long chatId = callbackQuery.path("message").path("chat").path("id").asLong();
    int messageId = callbackQuery.path("message").path("message_id").asInt();

    if (!telegramProperties.bot().isChatAllowed(chatId)) {
      log.warn("Rejected callback from unauthorized chat: {}", chatId);
      return;
    }

    if (rateLimitService.isChatRateLimited(chatId)) {
      telegramClient.answerCallbackQuery(callbackQueryId, null);
      return;
    }

    // Acknowledge the callback immediately
    telegramClient.answerCallbackQuery(callbackQueryId, null);

    // Extract thread ID and resolve user locale
    JsonNode messageNode = callbackQuery.path("message");
    Integer messageThreadId =
        messageNode.has("message_thread_id")
            ? messageNode.path("message_thread_id").asInt()
            : null;
    String languageCode = callbackQuery.path("from").path("language_code").asText(null);
    String username = callbackQuery.path("from").path("username").asText(null);
    var botUser =
        botUserService.resolveUser(userId, chatId, languageCode, messageThreadId, username);
    Locale locale =
        edu.kpi.fice.telegram_service.i18n.BotMessageResolver.resolveLocale(
            botUser.getLanguageCode());

    BotResponse response =
        callbackDispatcher.dispatch(callbackData, userId, chatId, messageId, locale);
    telegramClient.sendResponse(chatId, response, messageThreadId);
  }

  @GetMapping("/health")
  public ResponseEntity<String> health() {
    return ResponseEntity.ok("Bot service healthy");
  }
}
