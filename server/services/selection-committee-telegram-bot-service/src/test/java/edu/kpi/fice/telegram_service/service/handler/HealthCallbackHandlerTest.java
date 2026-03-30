package edu.kpi.fice.telegram_service.service.handler;

import static org.assertj.core.api.Assertions.assertThat;

import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;

@ExtendWith(MockitoExtension.class)
@DisplayName("HealthCallbackHandler")
class HealthCallbackHandlerTest {

  private static final Locale LOCALE = Locale.ENGLISH;
  private static final Long USER_ID = 1L;
  private static final Long CHAT_ID = 10L;
  private static final Integer MESSAGE_ID = 7;

  private HealthCallbackHandler handler;

  @BeforeEach
  void setUp() {
    var source = new ResourceBundleMessageSource();
    source.setBasename("messages/messages");
    source.setDefaultEncoding("UTF-8");
    source.setUseCodeAsDefaultMessage(true);
    BotMessageResolver msg = new BotMessageResolver(source);
    Map<String, String> endpoints = new LinkedHashMap<>();
    endpoints.put("TestService", "http://localhost:99999/actuator/health");
    handler = new HealthCallbackHandler(msg, endpoints, 1000);
  }

  @Nested
  @DisplayName("supports")
  class Supports {

    @Test
    @DisplayName("returns true for 'health' module")
    void supportsHealth() {
      assertThat(handler.supports("health")).isTrue();
    }

    @Test
    @DisplayName("returns false for 'flags'")
    void doesNotSupportFlags() {
      assertThat(handler.supports("flags")).isFalse();
    }
  }

  @Nested
  @DisplayName("handle")
  class Handle {

    @Test
    @DisplayName("returns an edit response")
    void handle_returnsEditResponse() {
      BotResponse result = handler.handle("health:services", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.isEdit()).isTrue();
      assertThat(result.editMessageId()).isEqualTo(MESSAGE_ID);
    }

    @Test
    @DisplayName("response text contains health title")
    void handle_textContainsHealthTitle() {
      BotResponse result = handler.handle("health:services", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.text()).contains("System Health");
    }

    @Test
    @DisplayName("response text lists configured services")
    void handle_textContainsConfiguredServices() {
      BotResponse result = handler.handle("health:services", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.text()).contains("TestService");
    }

    @Test
    @DisplayName("response text contains checked timestamp")
    void handle_textContainsTimestamp() {
      BotResponse result = handler.handle("health:services", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.text()).contains("Checked:");
    }

    @Test
    @DisplayName("response keyboard contains refresh and nav buttons")
    void handle_keyboardContainsButtons() {
      BotResponse result = handler.handle("health:services", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.keyboard())
          .anyMatch(row -> row.stream().anyMatch(b -> b.callbackData().equals("health:services")));
      assertThat(result.keyboard())
          .anyMatch(row -> row.stream().anyMatch(b -> b.callbackData().equals("menu:main")));
    }

    @Test
    @DisplayName("services are reported as Offline when unreachable")
    void handle_servicesAreOffline_whenUnreachable() {
      BotResponse result = handler.handle("health:services", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.text()).contains("Offline");
    }
  }

  @Nested
  @DisplayName("defaults")
  class Defaults {

    @Test
    @DisplayName("uses default endpoints when empty map is provided")
    void usesDefaultEndpoints_whenEmptyMapProvided() {
      var source = new ResourceBundleMessageSource();
      source.setBasename("messages/messages");
      source.setDefaultEncoding("UTF-8");
      source.setUseCodeAsDefaultMessage(true);
      BotMessageResolver msg = new BotMessageResolver(source);

      HealthCallbackHandler defaultHandler = new HealthCallbackHandler(msg, Map.of(), 1000);
      BotResponse result =
          defaultHandler.handle("health:services", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.text()).contains("Environment");
      assertThat(result.text()).contains("Identity");
    }
  }
}
