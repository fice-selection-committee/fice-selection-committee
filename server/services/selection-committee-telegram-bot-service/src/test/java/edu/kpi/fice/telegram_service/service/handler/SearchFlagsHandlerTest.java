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
@DisplayName("SearchFlagsHandler")
class SearchFlagsHandlerTest {

  private static final Locale LOCALE = Locale.ENGLISH;

  @Mock private EnvironmentServiceClient envClient;

  private SearchFlagsHandler handler;

  @BeforeEach
  void setUp() {
    var source = new ResourceBundleMessageSource();
    source.setBasename("messages/messages");
    source.setDefaultEncoding("UTF-8");
    source.setUseCodeAsDefaultMessage(true);
    BotMessageResolver msg = new BotMessageResolver(source);
    handler = new SearchFlagsHandler(envClient, msg);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // supports
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("supports")
  class Supports {

    @Test
    @DisplayName("returns true for /search")
    void supportsSearch() {
      assertThat(handler.supports("/search")).isTrue();
    }

    @Test
    @DisplayName("returns false for /flags")
    void doesNotSupportFlags() {
      assertThat(handler.supports("/flags")).isFalse();
    }

    @Test
    @DisplayName("returns false for /flag")
    void doesNotSupportFlag() {
      assertThat(handler.supports("/flag")).isFalse();
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

      assertThat(result.text()).contains("Usage: /search");
      assertThat(result.text()).contains("<query>");
      assertThat(result.text()).contains("dark");
    }

    @Test
    @DisplayName("returns matching flags when key matches the query")
    void handle_returnsMatchingFlags_whenKeyMatches() {
      FeatureFlagResponse darkMode =
          new FeatureFlagResponse("dark-mode", true, null, null, null, null, null, null);
      FeatureFlagResponse darkTheme =
          new FeatureFlagResponse("dark-theme", false, null, null, null, null, null, null);
      FeatureFlagResponse betaFeature =
          new FeatureFlagResponse("beta-feature", true, null, null, null, null, null, null);
      when(envClient.getFlags(null, null)).thenReturn(List.of(darkMode, darkTheme, betaFeature));

      BotResponse result = handler.handle(new String[] {"dark"}, LOCALE);

      assertThat(result.text()).contains("<code>dark-mode</code>");
      assertThat(result.text()).contains("<code>dark-theme</code>");
      assertThat(result.text()).doesNotContain("beta-feature");
      assertThat(result.text()).contains("Found: 2");
    }

    @Test
    @DisplayName("returns no results message when nothing matches")
    void handle_returnsNoResultsMessage_whenNothingMatches() {
      FeatureFlagResponse unrelated =
          new FeatureFlagResponse(
              "payments-v2", true, "Payment processing", null, null, null, null, null);
      when(envClient.getFlags(null, null)).thenReturn(List.of(unrelated));

      BotResponse result = handler.handle(new String[] {"dark"}, LOCALE);

      assertThat(result.text()).contains("No flags matching");
      assertThat(result.text()).contains("dark");
    }

    @Test
    @DisplayName("searches description text in addition to key")
    void handle_searchesDescription_inAdditionToKey() {
      FeatureFlagResponse withMatchingDesc =
          new FeatureFlagResponse(
              "feature-x", true, "Enables dark mode support", null, null, null, null, null);
      FeatureFlagResponse withoutMatch =
          new FeatureFlagResponse(
              "feature-y", false, "Enables payments", null, null, null, null, null);
      when(envClient.getFlags(null, null)).thenReturn(List.of(withMatchingDesc, withoutMatch));

      BotResponse result = handler.handle(new String[] {"dark"}, LOCALE);

      assertThat(result.text()).contains("<code>feature-x</code>");
      assertThat(result.text()).doesNotContain("feature-y");
      assertThat(result.text()).contains("Found: 1");
    }

    @Test
    @DisplayName("description is included in the result line when present")
    void handle_includesDescription_inResultLine() {
      FeatureFlagResponse flag =
          new FeatureFlagResponse(
              "dark-mode", true, "Enables dark mode", null, null, null, null, null);
      when(envClient.getFlags(null, null)).thenReturn(List.of(flag));

      BotResponse result = handler.handle(new String[] {"dark"}, LOCALE);

      assertThat(result.text()).contains("Enables dark mode");
    }

    @Test
    @DisplayName("search is case-insensitive for both key and description")
    void handle_searchIsCaseInsensitive() {
      FeatureFlagResponse flag =
          new FeatureFlagResponse(
              "DARK-MODE", true, "Enables DARK Mode", null, null, null, null, null);
      when(envClient.getFlags(null, null)).thenReturn(List.of(flag));

      BotResponse result = handler.handle(new String[] {"dark"}, LOCALE);

      assertThat(result.text()).contains("Found: 1");
    }

    @Test
    @DisplayName("multi-word query is joined and matched as a single lowercase phrase")
    void handle_multiWordQuery_isJoinedAndMatched() {
      FeatureFlagResponse flag =
          new FeatureFlagResponse(
              "dark-mode", true, "feature for dark mode switching", null, null, null, null, null);
      when(envClient.getFlags(null, null)).thenReturn(List.of(flag));

      // dispatcher would normally split on spaces; handler receives the tokens as separate args
      BotResponse result = handler.handle(new String[] {"dark", "mode"}, LOCALE);

      assertThat(result.text()).contains("Found: 1");
    }

    @Test
    @DisplayName("returns error message when environment client throws an exception")
    void handle_returnsErrorMessage_whenClientFails() {
      when(envClient.getFlags(null, null)).thenThrow(new RuntimeException("service down"));

      BotResponse result = handler.handle(new String[] {"dark"}, LOCALE);

      assertThat(result.text()).isEqualTo("Search failed. Environment service may be unavailable.");
    }

    @Test
    @DisplayName("result header contains the search query")
    void handle_resultHeader_containsSearchQuery() {
      FeatureFlagResponse flag =
          new FeatureFlagResponse("dark-mode", true, null, null, null, null, null, null);
      when(envClient.getFlags(null, null)).thenReturn(List.of(flag));

      BotResponse result = handler.handle(new String[] {"dark"}, LOCALE);

      assertThat(result.text()).contains("<b>Search results for:");
      assertThat(result.text()).contains("dark");
    }
  }
}
