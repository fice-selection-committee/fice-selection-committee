package edu.kpi.fice.telegram_service.service.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import edu.kpi.fice.telegram_service.client.EnvironmentServiceClient;
import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.dto.FeatureFlagResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
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
@DisplayName("ViewFlagHandler")
class ViewFlagHandlerTest {

  private static final Locale LOCALE = Locale.ENGLISH;

  @Mock private EnvironmentServiceClient envClient;

  private ViewFlagHandler handler;

  @BeforeEach
  void setUp() {
    var source = new ResourceBundleMessageSource();
    source.setBasename("messages/messages");
    source.setDefaultEncoding("UTF-8");
    source.setUseCodeAsDefaultMessage(true);
    BotMessageResolver msg = new BotMessageResolver(source);
    handler = new ViewFlagHandler(envClient, msg);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // supports
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("supports")
  class Supports {

    @Test
    @DisplayName("returns true for /flag")
    void supportsFlag() {
      assertThat(handler.supports("/flag")).isTrue();
    }

    @Test
    @DisplayName("returns false for /flags (plural)")
    void doesNotSupportFlagsPlural() {
      assertThat(handler.supports("/flags")).isFalse();
    }

    @Test
    @DisplayName("returns false for /search")
    void doesNotSupportSearch() {
      assertThat(handler.supports("/search")).isFalse();
    }

    @Test
    @DisplayName("returns false for /help")
    void doesNotSupportHelp() {
      assertThat(handler.supports("/help")).isFalse();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // handle
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handle")
  class Handle {

    @Test
    @DisplayName("returns usage hint when no args provided")
    void handle_returnsUsage_whenNoArgs() {
      BotResponse result = handler.handle(new String[0], LOCALE);

      assertThat(result.text()).contains("Usage: /flag");
      assertThat(result.text()).contains("<key>");
      assertThat(result.text()).contains("dark-mode");
    }

    @Test
    @DisplayName("returns full flag details for an enabled flag with all optional fields")
    void handle_returnsFlagDetails_enabledWithAllFields() {
      FeatureFlagResponse flag =
          new FeatureFlagResponse(
              "dark-mode",
              true,
              "Switches UI to dark theme",
              "RELEASE",
              "team-ui",
              "ACTIVE",
              80,
              "2026-12-31");
      when(envClient.getFlagByKey("dark-mode")).thenReturn(flag);

      BotResponse result = handler.handle(new String[] {"dark-mode"}, LOCALE);

      assertThat(result.text()).contains("dark-mode");
      assertThat(result.text()).contains("Enabled");
      assertThat(result.text()).contains("<b>Status:</b> ACTIVE");
      assertThat(result.text()).contains("<b>Type:</b> RELEASE");
      assertThat(result.text()).contains("<b>Owner:</b> team-ui");
      assertThat(result.text()).contains("<b>Description:</b> Switches UI to dark theme");
      assertThat(result.text()).contains("<b>Rollout:</b> 80%");
      assertThat(result.text()).contains("<b>Expires:</b> 2026-12-31");
    }

    @Test
    @DisplayName("returns flag details for a disabled flag")
    void handle_returnsFlagDetails_disabled() {
      FeatureFlagResponse flag =
          new FeatureFlagResponse("beta", false, null, null, null, null, null, null);
      when(envClient.getFlagByKey("beta")).thenReturn(flag);

      BotResponse result = handler.handle(new String[] {"beta"}, LOCALE);

      assertThat(result.text()).contains("beta");
      assertThat(result.text()).contains("Disabled");
    }

    @Test
    @DisplayName("omits optional sections when their fields are null")
    void handle_omitsOptionalSections_whenFieldsAreNull() {
      FeatureFlagResponse flag =
          new FeatureFlagResponse("minimal", true, null, null, null, null, null, null);
      when(envClient.getFlagByKey("minimal")).thenReturn(flag);

      BotResponse result = handler.handle(new String[] {"minimal"}, LOCALE);

      assertThat(result.text()).doesNotContain("<b>Status:</b>");
      assertThat(result.text()).doesNotContain("<b>Type:</b>");
      assertThat(result.text()).doesNotContain("<b>Owner:</b>");
      assertThat(result.text()).doesNotContain("<b>Description:</b>");
      assertThat(result.text()).doesNotContain("<b>Rollout:</b>");
      assertThat(result.text()).doesNotContain("<b>Expires:</b>");
    }

    @Test
    @DisplayName("omits rollout line when rollout percentage is exactly 100")
    void handle_omitsRollout_whenRolloutIs100() {
      FeatureFlagResponse flag =
          new FeatureFlagResponse("full-rollout", true, null, null, null, null, 100, null);
      when(envClient.getFlagByKey("full-rollout")).thenReturn(flag);

      BotResponse result = handler.handle(new String[] {"full-rollout"}, LOCALE);

      assertThat(result.text()).doesNotContain("<b>Rollout:</b>");
    }

    @Test
    @DisplayName("shows rollout line when rollout percentage is below 100")
    void handle_showsRollout_whenRolloutBelow100() {
      FeatureFlagResponse flag =
          new FeatureFlagResponse("partial", true, null, null, null, null, 50, null);
      when(envClient.getFlagByKey("partial")).thenReturn(flag);

      BotResponse result = handler.handle(new String[] {"partial"}, LOCALE);

      assertThat(result.text()).contains("<b>Rollout:</b> 50%");
    }

    @Test
    @DisplayName("returns error message when flag is not found (client throws)")
    void handle_returnsErrorMessage_whenFlagNotFound() {
      when(envClient.getFlagByKey("nonexistent")).thenThrow(new RuntimeException("404 Not Found"));

      BotResponse result = handler.handle(new String[] {"nonexistent"}, LOCALE);

      assertThat(result.text()).contains("nonexistent");
      assertThat(result.text()).contains("not found or service unavailable");
    }

    @Test
    @DisplayName("uses only first arg as key and ignores subsequent args")
    void handle_usesFirstArgAsKey() {
      FeatureFlagResponse flag =
          new FeatureFlagResponse("my-flag", true, null, null, null, null, null, null);
      when(envClient.getFlagByKey("my-flag")).thenReturn(flag);

      BotResponse result = handler.handle(new String[] {"my-flag", "extra"}, LOCALE);

      assertThat(result.text()).contains("my-flag");
    }
  }
}
