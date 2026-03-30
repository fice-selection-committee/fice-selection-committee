package edu.kpi.fice.telegram_service.service.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import edu.kpi.fice.telegram_service.client.EnvironmentServiceClient;
import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.dto.FeatureFlagResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
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
@DisplayName("ListFlagsHandler")
class ListFlagsHandlerTest {

  private static final Locale LOCALE = Locale.ENGLISH;

  @Mock private EnvironmentServiceClient envClient;

  private ListFlagsHandler handler;

  @BeforeEach
  void setUp() {
    var source = new ResourceBundleMessageSource();
    source.setBasename("messages/messages");
    source.setDefaultEncoding("UTF-8");
    source.setUseCodeAsDefaultMessage(true);
    BotMessageResolver msg = new BotMessageResolver(source);
    handler = new ListFlagsHandler(envClient, msg);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // supports
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("supports")
  class Supports {

    @Test
    @DisplayName("returns true for /flags")
    void supportsFlags() {
      assertThat(handler.supports("/flags")).isTrue();
    }

    @Test
    @DisplayName("returns false for /flag (singular)")
    void doesNotSupportFlagSingular() {
      assertThat(handler.supports("/flag")).isFalse();
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

    @Test
    @DisplayName("returns false for an arbitrary string")
    void doesNotSupportArbitraryString() {
      assertThat(handler.supports("/unknown")).isFalse();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // handle
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handle")
  class Handle {

    @Test
    @DisplayName("returns formatted flag list when flags exist")
    void handle_returnFormattedList_whenFlagsExist() {
      FeatureFlagResponse enabledFlag =
          new FeatureFlagResponse(
              "dark-mode", true, "Enables dark mode", "RELEASE", "team-ui", "ACTIVE", 100, null);
      FeatureFlagResponse disabledFlag =
          new FeatureFlagResponse(
              "beta-feature", false, null, "EXPERIMENT", null, "INACTIVE", null, null);
      when(envClient.getFlags(null, null)).thenReturn(List.of(enabledFlag, disabledFlag));

      BotResponse result = handler.handle(new String[0], LOCALE);

      assertThat(result.text()).contains("<b>");
      assertThat(result.text()).contains("Feature Flags");
      assertThat(result.text()).contains("<code>dark-mode</code>");
      assertThat(result.text()).contains("[ACTIVE]");
      assertThat(result.text()).contains("(RELEASE)");
      assertThat(result.text()).contains("<code>beta-feature</code>");
      assertThat(result.text()).contains("[INACTIVE]");
      assertThat(result.text()).contains("(EXPERIMENT)");
      assertThat(result.text()).contains("Total: 2");
    }

    @Test
    @DisplayName("enabled flag gets green circle icon and disabled flag gets red circle icon")
    void handle_enabledFlagGetsGreenIcon_disabledFlagGetsRedIcon() {
      FeatureFlagResponse enabledFlag =
          new FeatureFlagResponse("on-flag", true, null, null, null, null, null, null);
      FeatureFlagResponse disabledFlag =
          new FeatureFlagResponse("off-flag", false, null, null, null, null, null, null);
      when(envClient.getFlags(null, null)).thenReturn(List.of(enabledFlag, disabledFlag));

      BotResponse result = handler.handle(new String[0], LOCALE);

      // Green circle must appear before on-flag, red circle before off-flag
      int greenPos = result.text().indexOf("\uD83D\uDFE2"); // 🟢
      int onFlagPos = result.text().indexOf("on-flag");
      int redPos = result.text().indexOf("\uD83D\uDD34"); // 🔴
      int offFlagPos = result.text().indexOf("off-flag");
      assertThat(greenPos).isLessThan(onFlagPos);
      assertThat(redPos).isLessThan(offFlagPos);
    }

    @Test
    @DisplayName("flag without status and type omits the bracket and paren sections")
    void handle_flagWithNullStatusAndType_omitsBracketsAndParens() {
      FeatureFlagResponse minimalFlag =
          new FeatureFlagResponse("minimal", true, null, null, null, null, null, null);
      when(envClient.getFlags(null, null)).thenReturn(List.of(minimalFlag));

      BotResponse result = handler.handle(new String[0], LOCALE);

      assertThat(result.text()).contains("<code>minimal</code>");
      assertThat(result.text()).doesNotContain("[");
      assertThat(result.text()).doesNotContain("(");
    }

    @Test
    @DisplayName("returns empty message when no flags exist")
    void handle_returnsEmptyMessage_whenNoFlags() {
      when(envClient.getFlags(null, null)).thenReturn(List.of());

      BotResponse result = handler.handle(new String[0], LOCALE);

      assertThat(result.text()).isEqualTo("No feature flags found.");
    }

    @Test
    @DisplayName("returns error message when environment client throws an exception")
    void handle_returnsErrorMessage_whenClientFails() {
      when(envClient.getFlags(null, null)).thenThrow(new RuntimeException("connection refused"));

      BotResponse result = handler.handle(new String[0], LOCALE);

      assertThat(result.text())
          .isEqualTo("Failed to fetch flags. Environment service may be unavailable.");
    }

    @Test
    @DisplayName("args are ignored — the handler always lists all flags")
    void handle_argsAreIgnored() {
      when(envClient.getFlags(null, null)).thenReturn(List.of());

      BotResponse result = handler.handle(new String[] {"some", "extra", "args"}, LOCALE);

      assertThat(result.text()).isEqualTo("No feature flags found.");
    }
  }
}
