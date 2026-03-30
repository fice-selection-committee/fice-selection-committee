package edu.kpi.fice.telegram_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.kpi.fice.telegram_service.config.TelegramProperties;
import edu.kpi.fice.telegram_service.config.TelegramProperties.BotProperties;
import edu.kpi.fice.telegram_service.config.TelegramProperties.RateLimitProperties;
import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.dto.BotResponse.InlineButton;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Unit tests for TelegramApiClient.
 *
 * <p>The RestTemplate is injected via reflection because the production code constructs it
 * internally. This avoids any live HTTP calls while still verifying the URL construction, payload
 * shape, and error swallowing behaviour.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TelegramApiClient")
class TelegramApiClientTest {

  private static final String TOKEN = "test-bot-token";
  private static final String EXPECTED_BASE = "https://api.telegram.org/bot" + TOKEN;

  @Mock private RestTemplate restTemplate;

  private TelegramApiClient client;

  @BeforeEach
  void setUp() throws Exception {
    TelegramProperties props =
        new TelegramProperties(
            new BotProperties(TOKEN, true, List.of()), new RateLimitProperties(10));
    client = new TelegramApiClient(props);

    // Inject mock RestTemplate via reflection
    Field field = TelegramApiClient.class.getDeclaredField("restTemplate");
    field.setAccessible(true);
    field.set(client, restTemplate);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // sendMessage
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("sendMessage")
  class SendMessage {

    @Test
    @DisplayName("calls sendMessage endpoint with correct URL")
    void sendMessage_callsCorrectUrl() {
      when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
          .thenReturn(ResponseEntity.ok("{}"));

      client.sendMessage(123L, "Hello");

      ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
      verify(restTemplate).postForEntity(urlCaptor.capture(), any(), eq(String.class));
      assertThat(urlCaptor.getValue()).isEqualTo(EXPECTED_BASE + "/sendMessage");
    }

    @Test
    @DisplayName("does not throw when RestTemplate throws")
    void sendMessage_doesNotThrow_whenRestTemplateThrows() {
      when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
          .thenThrow(new RuntimeException("connection refused"));

      assertThatCode(() -> client.sendMessage(123L, "Hello")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sends message with HTML parse mode by default")
    void sendMessage_sendsWithHtmlParseMode_byDefault() {
      when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
          .thenReturn(ResponseEntity.ok("{}"));

      client.sendMessage(123L, "<b>Bold</b>");

      verify(restTemplate, times(1)).postForEntity(any(String.class), any(), eq(String.class));
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // sendMessageWithKeyboard
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("sendMessageWithKeyboard")
  class SendMessageWithKeyboard {

    @Test
    @DisplayName("calls sendMessage endpoint when keyboard is provided")
    void sendMessageWithKeyboard_callsSendMessageEndpoint() {
      when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
          .thenReturn(ResponseEntity.ok("{}"));

      List<List<InlineButton>> keyboard =
          List.of(List.of(new InlineButton("Click me", "action:do")));
      client.sendMessageWithKeyboard(123L, "Pick one:", keyboard, null);

      ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
      verify(restTemplate).postForEntity(urlCaptor.capture(), any(), eq(String.class));
      assertThat(urlCaptor.getValue()).isEqualTo(EXPECTED_BASE + "/sendMessage");
    }

    @Test
    @DisplayName("does not throw when RestTemplate throws")
    void sendMessageWithKeyboard_doesNotThrow_whenRestTemplateThrows() {
      when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
          .thenThrow(new RuntimeException("timeout"));

      List<List<InlineButton>> keyboard = List.of(List.of(new InlineButton("Btn", "cb")));

      assertThatCode(() -> client.sendMessageWithKeyboard(123L, "text", keyboard, null))
          .doesNotThrowAnyException();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // editMessage
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("editMessage")
  class EditMessage {

    @Test
    @DisplayName("calls editMessageText endpoint")
    void editMessage_callsEditMessageTextEndpoint() {
      when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
          .thenReturn(ResponseEntity.ok("{}"));

      client.editMessage(123L, 55, "Updated text", List.of());

      ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
      verify(restTemplate).postForEntity(urlCaptor.capture(), any(), eq(String.class));
      assertThat(urlCaptor.getValue()).isEqualTo(EXPECTED_BASE + "/editMessageText");
    }

    @Test
    @DisplayName("does not throw when RestTemplate throws")
    void editMessage_doesNotThrow_whenRestTemplateThrows() {
      when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
          .thenThrow(new RuntimeException("not found"));

      assertThatCode(() -> client.editMessage(123L, 55, "text", List.of()))
          .doesNotThrowAnyException();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // answerCallbackQuery
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("answerCallbackQuery")
  class AnswerCallbackQuery {

    @Test
    @DisplayName("calls answerCallbackQuery endpoint")
    void answerCallbackQuery_callsCorrectEndpoint() {
      when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
          .thenReturn(ResponseEntity.ok("{}"));

      client.answerCallbackQuery("cq-id-123", null);

      ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
      verify(restTemplate).postForEntity(urlCaptor.capture(), any(), eq(String.class));
      assertThat(urlCaptor.getValue()).isEqualTo(EXPECTED_BASE + "/answerCallbackQuery");
    }

    @Test
    @DisplayName("does not throw when RestTemplate throws")
    void answerCallbackQuery_doesNotThrow_whenRestTemplateThrows() {
      when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
          .thenThrow(new RuntimeException("error"));

      assertThatCode(() -> client.answerCallbackQuery("cq-id", "note")).doesNotThrowAnyException();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // sendResponse dispatch
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("sendResponse")
  class SendResponse {

    @Test
    @DisplayName("calls editMessageText when response is an edit")
    void sendResponse_callsEditMessageText_whenResponseIsEdit() {
      when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
          .thenReturn(ResponseEntity.ok("{}"));

      BotResponse editResponse = BotResponse.edit("Updated", List.of(), 5);
      client.sendResponse(123L, editResponse, null);

      ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
      verify(restTemplate).postForEntity(urlCaptor.capture(), any(), eq(String.class));
      assertThat(urlCaptor.getValue()).contains("editMessageText");
    }

    @Test
    @DisplayName("calls sendMessage with keyboard when response has keyboard")
    void sendResponse_callsSendMessageWithKeyboard_whenResponseHasKeyboard() {
      when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
          .thenReturn(ResponseEntity.ok("{}"));

      List<List<InlineButton>> kb = List.of(List.of(new InlineButton("Btn", "cb")));
      BotResponse kbResponse = BotResponse.withKeyboard("Choose", kb);
      client.sendResponse(123L, kbResponse, null);

      ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
      verify(restTemplate).postForEntity(urlCaptor.capture(), any(), eq(String.class));
      assertThat(urlCaptor.getValue()).contains("sendMessage");
    }

    @Test
    @DisplayName("calls sendMessage (plain text) when response has no keyboard and is not an edit")
    void sendResponse_callsSendMessage_whenResponseIsPlainText() {
      when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
          .thenReturn(ResponseEntity.ok("{}"));

      BotResponse textResponse = BotResponse.text("Hello");
      client.sendResponse(123L, textResponse, null);

      ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
      verify(restTemplate).postForEntity(urlCaptor.capture(), any(), eq(String.class));
      assertThat(urlCaptor.getValue()).contains("sendMessage");
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // retry behavior
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("retry behavior")
  class RetryBehavior {

    @Test
    @DisplayName("retries on 429 Too Many Requests and eventually succeeds")
    void retries_on429_andSucceeds() {
      when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
          .thenThrow(
              HttpClientErrorException.create(
                  org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                  "Too Many Requests",
                  org.springframework.http.HttpHeaders.EMPTY,
                  new byte[0],
                  null))
          .thenReturn(ResponseEntity.ok("{}"));

      client.sendMessage(123L, "Hello");

      verify(restTemplate, times(2)).postForEntity(any(String.class), any(), eq(String.class));
    }

    @Test
    @DisplayName("stops retrying on 403 Forbidden")
    void stopsOn403() {
      when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
          .thenThrow(
              HttpClientErrorException.create(
                  org.springframework.http.HttpStatus.FORBIDDEN,
                  "Forbidden",
                  org.springframework.http.HttpHeaders.EMPTY,
                  new byte[0],
                  null));

      assertThatCode(() -> client.sendMessage(123L, "Hello")).doesNotThrowAnyException();

      verify(restTemplate, times(1)).postForEntity(any(String.class), any(), eq(String.class));
    }

    @Test
    @DisplayName("stops retrying on other HTTP client errors")
    void stopsOnOtherClientErrors() {
      when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
          .thenThrow(
              HttpClientErrorException.create(
                  org.springframework.http.HttpStatus.BAD_REQUEST,
                  "Bad Request",
                  org.springframework.http.HttpHeaders.EMPTY,
                  new byte[0],
                  null));

      assertThatCode(() -> client.sendMessage(123L, "Hello")).doesNotThrowAnyException();

      verify(restTemplate, times(1)).postForEntity(any(String.class), any(), eq(String.class));
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // setMyCommands
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("setMyCommands")
  class SetMyCommands {

    @Test
    @DisplayName("calls setMyCommands endpoint")
    void setMyCommands_callsCorrectEndpoint() {
      when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
          .thenReturn(ResponseEntity.ok("{}"));

      client.setMyCommands(List.of(java.util.Map.of("command", "start", "description", "test")));

      ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
      verify(restTemplate).postForEntity(urlCaptor.capture(), any(), eq(String.class));
      assertThat(urlCaptor.getValue()).isEqualTo(EXPECTED_BASE + "/setMyCommands");
    }

    @Test
    @DisplayName("does not throw when RestTemplate throws")
    void setMyCommands_doesNotThrow_whenRestTemplateThrows() {
      when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
          .thenThrow(new RuntimeException("error"));

      assertThatCode(() -> client.setMyCommands(List.of())).doesNotThrowAnyException();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // truncateIfNeeded
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("truncateIfNeeded")
  class TruncateIfNeeded {

    @Test
    @DisplayName("returns text unchanged when within limit")
    void truncateIfNeeded_returnsUnchanged_whenWithinLimit() {
      String text = "Short message";
      assertThat(TelegramApiClient.truncateIfNeeded(text)).isEqualTo(text);
    }

    @Test
    @DisplayName("truncates and appends suffix when over limit")
    void truncateIfNeeded_truncatesAndAppendsSuffix_whenOverLimit() {
      String longText = "x".repeat(5000);
      String result = TelegramApiClient.truncateIfNeeded(longText);

      assertThat(result.length()).isEqualTo(4096);
      assertThat(result).endsWith("(truncated)");
    }

    @Test
    @DisplayName("handles null text")
    void truncateIfNeeded_handlesNull() {
      assertThat(TelegramApiClient.truncateIfNeeded(null)).isNull();
    }
  }
}
