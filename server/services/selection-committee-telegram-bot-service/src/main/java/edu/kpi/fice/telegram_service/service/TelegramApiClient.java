package edu.kpi.fice.telegram_service.service;

import edu.kpi.fice.telegram_service.config.TelegramProperties;
import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.dto.BotResponse.InlineButton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "true")
public class TelegramApiClient {

  private static final String API_BASE = "https://api.telegram.org/bot";
  private static final int MAX_MESSAGE_LENGTH = 4096;
  private static final String TRUNCATION_SUFFIX = "\n... (truncated)";
  private static final int MAX_RETRIES = 3;

  private final String botToken;
  private final RestTemplate restTemplate;

  public TelegramApiClient(TelegramProperties props) {
    this.botToken = props.bot().token();
    this.restTemplate = new RestTemplate();
  }

  public void sendMessage(Long chatId, String text) {
    sendMessage(chatId, text, "HTML", null);
  }

  public void sendMessage(Long chatId, String text, Integer messageThreadId) {
    sendMessage(chatId, text, "HTML", messageThreadId);
  }

  public void sendMessage(Long chatId, String text, String parseMode, Integer messageThreadId) {
    String url = API_BASE + botToken + "/sendMessage";
    String safeText = truncateIfNeeded(text);
    executeWithRetry(
        () -> {
          Map<String, Object> body = new LinkedHashMap<>();
          body.put("chat_id", chatId);
          body.put("text", safeText);
          body.put("parse_mode", parseMode);
          if (messageThreadId != null) {
            body.put("message_thread_id", messageThreadId);
          }
          restTemplate.postForEntity(url, body, String.class);
        },
        "sendMessage",
        chatId);
  }

  public void sendResponse(Long chatId, BotResponse response, Integer messageThreadId) {
    if (response.isEdit()) {
      editMessage(chatId, response.editMessageId(), response.text(), response.keyboard());
    } else if (response.hasKeyboard()) {
      sendMessageWithKeyboard(chatId, response.text(), response.keyboard(), messageThreadId);
    } else {
      sendMessage(chatId, response.text(), messageThreadId);
    }
  }

  public void sendMessageWithKeyboard(
      Long chatId, String text, List<List<InlineButton>> keyboard, Integer messageThreadId) {
    String url = API_BASE + botToken + "/sendMessage";
    String safeText = truncateIfNeeded(text);
    executeWithRetry(
        () -> {
          Map<String, Object> body = new LinkedHashMap<>();
          body.put("chat_id", chatId);
          body.put("text", safeText);
          body.put("parse_mode", "HTML");
          if (messageThreadId != null) {
            body.put("message_thread_id", messageThreadId);
          }
          body.put("reply_markup", buildInlineKeyboardMarkup(keyboard));
          restTemplate.postForEntity(url, body, String.class);
        },
        "sendMessageWithKeyboard",
        chatId);
  }

  public void editMessage(
      Long chatId, Integer messageId, String text, List<List<InlineButton>> keyboard) {
    String url = API_BASE + botToken + "/editMessageText";
    String safeText = truncateIfNeeded(text);
    executeWithRetry(
        () -> {
          Map<String, Object> body = new LinkedHashMap<>();
          body.put("chat_id", chatId);
          body.put("message_id", messageId);
          body.put("text", safeText);
          body.put("parse_mode", "HTML");
          if (keyboard != null && !keyboard.isEmpty()) {
            body.put("reply_markup", buildInlineKeyboardMarkup(keyboard));
          }
          restTemplate.postForEntity(url, body, String.class);
        },
        "editMessage",
        chatId);
  }

  public void answerCallbackQuery(String callbackQueryId, String notification) {
    String url = API_BASE + botToken + "/answerCallbackQuery";
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("callback_query_id", callbackQueryId);
      if (notification != null) {
        body.put("text", notification);
      }

      restTemplate.postForEntity(url, body, String.class);
    } catch (Exception e) {
      log.error("Failed to answer callback query {}: {}", callbackQueryId, e.getMessage());
    }
  }

  public void setMyCommands(List<Map<String, String>> commands) {
    String url = API_BASE + botToken + "/setMyCommands";
    try {
      restTemplate.postForEntity(url, Map.of("commands", commands), String.class);
      log.info("Registered {} bot commands with Telegram", commands.size());
    } catch (Exception e) {
      log.error("Failed to register bot commands: {}", e.getMessage());
    }
  }

  private Map<String, Object> buildInlineKeyboardMarkup(List<List<InlineButton>> keyboard) {
    List<List<Map<String, String>>> rows = new ArrayList<>();
    for (List<InlineButton> row : keyboard) {
      List<Map<String, String>> buttonRow = new ArrayList<>();
      for (InlineButton button : row) {
        buttonRow.add(Map.of("text", button.text(), "callback_data", button.callbackData()));
      }
      rows.add(buttonRow);
    }
    return Map.of("inline_keyboard", rows);
  }

  static String truncateIfNeeded(String text) {
    if (text == null || text.length() <= MAX_MESSAGE_LENGTH) {
      return text;
    }
    return text.substring(0, MAX_MESSAGE_LENGTH - TRUNCATION_SUFFIX.length()) + TRUNCATION_SUFFIX;
  }

  private void executeWithRetry(Runnable action, String operation, Long chatId) {
    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      try {
        action.run();
        log.info("{} to chat {} succeeded", operation, chatId);
        return;
      } catch (HttpClientErrorException e) {
        if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
          long backoffMs = (long) Math.pow(2, attempt) * 500;
          log.warn(
              "{} to chat {} rate limited (429), retrying in {}ms (attempt {}/{})",
              operation,
              chatId,
              backoffMs,
              attempt,
              MAX_RETRIES);
          try {
            Thread.sleep(backoffMs);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return;
          }
        } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
          log.error("{} to chat {} forbidden (403): bot may be blocked by user", operation, chatId);
          return;
        } else {
          log.error(
              "{} to chat {} failed with status {}: {}",
              operation,
              chatId,
              e.getStatusCode(),
              e.getMessage());
          return;
        }
      } catch (Exception e) {
        log.error("{} to chat {} failed: {}", operation, chatId, e.getMessage());
        return;
      }
    }
    log.error("{} to chat {} failed after {} retries", operation, chatId, MAX_RETRIES);
  }
}
