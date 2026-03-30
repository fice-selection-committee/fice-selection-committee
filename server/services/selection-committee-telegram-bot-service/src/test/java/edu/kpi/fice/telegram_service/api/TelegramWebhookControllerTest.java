package edu.kpi.fice.telegram_service.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kpi.fice.telegram_service.config.TelegramProperties;
import edu.kpi.fice.telegram_service.config.TelegramProperties.BotProperties;
import edu.kpi.fice.telegram_service.config.TelegramProperties.RateLimitProperties;
import edu.kpi.fice.telegram_service.domain.BotUser;
import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.security.RateLimitService;
import edu.kpi.fice.telegram_service.service.BotCommandDispatcher;
import edu.kpi.fice.telegram_service.service.BotUserService;
import edu.kpi.fice.telegram_service.service.CallbackQueryDispatcher;
import edu.kpi.fice.telegram_service.service.TelegramApiClient;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("TelegramWebhookController")
class TelegramWebhookControllerTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock private BotCommandDispatcher dispatcher;
  @Mock private CallbackQueryDispatcher callbackDispatcher;
  @Mock private TelegramApiClient telegramClient;
  @Mock private BotUserService botUserService;
  @Mock private RateLimitService rateLimitService;

  private TelegramWebhookController controller;

  @BeforeEach
  void setUp() {
    TelegramProperties props =
        new TelegramProperties(
            new BotProperties("test-token", true, List.of()), new RateLimitProperties(5));
    controller =
        new TelegramWebhookController(
            dispatcher,
            callbackDispatcher,
            telegramClient,
            props,
            botUserService,
            rateLimitService);
  }

  @Nested
  @DisplayName("text message handling")
  class TextMessageHandling {

    @Test
    @DisplayName("dispatches command and sends response")
    void dispatchesCommandAndSendsResponse() throws Exception {
      JsonNode update =
          MAPPER.readTree(
              """
          {
            "message": {
              "text": "/flags",
              "chat": {"id": 123},
              "from": {"id": 456, "language_code": "en"}
            }
          }
          """);
      BotUser user = BotUser.builder().telegramUserId(456L).chatId(123L).languageCode("en").build();
      when(botUserService.resolveUser(eq(456L), eq(123L), eq("en"), any(), any())).thenReturn(user);
      BotResponse response = BotResponse.text("flag list");
      when(dispatcher.dispatch(eq("/flags"), eq(123L), eq(456L), any(Locale.class), any()))
          .thenReturn(response);

      ResponseEntity<Void> result = controller.handleWebhook(update);

      assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
      verify(telegramClient).sendResponse(eq(123L), eq(response), any());
    }

    @Test
    @DisplayName("ignores non-command text")
    void ignoresNonCommandText() throws Exception {
      JsonNode update =
          MAPPER.readTree(
              """
          {
            "message": {
              "text": "hello world",
              "chat": {"id": 123},
              "from": {"id": 456}
            }
          }
          """);

      controller.handleWebhook(update);

      verify(dispatcher, never()).dispatch(any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("ignores missing message")
    void ignoresMissingMessage() throws Exception {
      JsonNode update = MAPPER.readTree("""
          {"update_id": 12345}
          """);

      ResponseEntity<Void> result = controller.handleWebhook(update);

      assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
      verify(dispatcher, never()).dispatch(any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("ignores blank text")
    void ignoresBlankText() throws Exception {
      JsonNode update =
          MAPPER.readTree(
              """
          {
            "message": {
              "text": "  ",
              "chat": {"id": 123},
              "from": {"id": 456}
            }
          }
          """);

      controller.handleWebhook(update);

      verify(dispatcher, never()).dispatch(any(), anyLong(), anyLong(), any(), any());
    }
  }

  @Nested
  @DisplayName("callback query handling")
  class CallbackQueryHandling {

    @Test
    @DisplayName("dispatches callback query and sends response")
    void dispatchesCallbackQuery() throws Exception {
      JsonNode update =
          MAPPER.readTree(
              """
          {
            "callback_query": {
              "id": "cb-123",
              "data": "flags:list:0",
              "from": {"id": 456, "language_code": "en"},
              "message": {
                "message_id": 789,
                "chat": {"id": 123}
              }
            }
          }
          """);
      BotUser user = BotUser.builder().telegramUserId(456L).chatId(123L).languageCode("en").build();
      when(botUserService.resolveUser(eq(456L), eq(123L), eq("en"), any(), any())).thenReturn(user);
      BotResponse response = BotResponse.text("flags result");
      when(callbackDispatcher.dispatch(
              eq("flags:list:0"), eq(456L), eq(123L), eq(789), any(Locale.class)))
          .thenReturn(response);

      controller.handleWebhook(update);

      verify(telegramClient).answerCallbackQuery("cb-123", null);
      verify(telegramClient).sendResponse(eq(123L), eq(response), any());
    }
  }

  @Nested
  @DisplayName("authorization")
  class Authorization {

    @Test
    @DisplayName("rejects message from unauthorized chat when whitelist is set")
    void rejectsUnauthorizedChat() throws Exception {
      TelegramProperties restrictedProps =
          new TelegramProperties(
              new BotProperties("test-token", true, List.of(999L)), new RateLimitProperties(5));
      TelegramWebhookController restrictedController =
          new TelegramWebhookController(
              dispatcher,
              callbackDispatcher,
              telegramClient,
              restrictedProps,
              botUserService,
              rateLimitService);

      JsonNode update =
          MAPPER.readTree(
              """
          {
            "message": {
              "text": "/flags",
              "chat": {"id": 123},
              "from": {"id": 456}
            }
          }
          """);

      restrictedController.handleWebhook(update);

      verify(dispatcher, never()).dispatch(any(), anyLong(), anyLong(), any(), any());
    }
  }

  @Nested
  @DisplayName("health endpoint")
  class HealthEndpoint {

    @Test
    @DisplayName("returns healthy status")
    void returnsHealthy() {
      ResponseEntity<String> result = controller.health();

      assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
      assertThat(result.getBody()).contains("healthy");
    }
  }
}
