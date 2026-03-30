package edu.kpi.fice.telegram_service.service.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.kpi.fice.telegram_service.client.EnvironmentServiceClient;
import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.dto.FeatureFlagResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import edu.kpi.fice.telegram_service.security.BotAuthorizationService;
import edu.kpi.fice.telegram_service.service.BotUserService;
import edu.kpi.fice.telegram_service.service.PendingToggleStore;
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
@DisplayName("ToggleFlagHandler")
class ToggleFlagHandlerTest {

  private static final Locale LOCALE = Locale.ENGLISH;

  @Mock private EnvironmentServiceClient envClient;
  @Mock private PendingToggleStore pendingToggleStore;
  @Mock private BotAuthorizationService authService;
  @Mock private BotUserService botUserService;

  private ToggleFlagHandler handler;

  @BeforeEach
  void setUp() {
    var source = new ResourceBundleMessageSource();
    source.setBasename("messages/messages");
    source.setDefaultEncoding("UTF-8");
    source.setUseCodeAsDefaultMessage(true);
    BotMessageResolver msg = new BotMessageResolver(source);
    handler = new ToggleFlagHandler(envClient, pendingToggleStore, msg, authService, botUserService);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // supports
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("supports")
  class Supports {

    @Test
    @DisplayName("returns true for /toggle")
    void supportsToggle() {
      assertThat(handler.supports("/toggle")).isTrue();
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
  }

  // ─────────────────────────────────────────────────────────────────────────
  // handle — usage
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handle — no args")
  class HandleNoArgs {

    @Test
    @DisplayName("returns usage message when no args are given")
    void handle_returnsUsage_whenNoArgs() {
      BotResponse result = handler.handle(new String[0], LOCALE);

      assertThat(result.text()).contains("Usage");
      assertThat(result.text()).contains("/toggle");
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // handle — confirmation prompt
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handle — confirmation prompt")
  class HandleConfirmationPrompt {

    @Test
    @DisplayName("shows confirmation prompt when only key is provided and flag exists")
    void handle_showsConfirmationPrompt_whenFlagExists() {
      FeatureFlagResponse flag =
          new FeatureFlagResponse("dark-mode", true, null, null, null, null, null, null);
      when(envClient.getFlagByKey("dark-mode")).thenReturn(flag);

      BotResponse result = handler.handle(new String[] {"dark-mode"}, LOCALE);

      assertThat(result.text()).contains("dark-mode");
      assertThat(result.text()).contains("Toggle Confirmation");
      verify(pendingToggleStore).store("dark-mode", false);
    }

    @Test
    @DisplayName("stores inverted state in pending store when flag is enabled")
    void handle_storesFalse_whenFlagIsEnabled() {
      FeatureFlagResponse enabledFlag =
          new FeatureFlagResponse("my-flag", true, null, null, null, null, null, null);
      when(envClient.getFlagByKey("my-flag")).thenReturn(enabledFlag);

      handler.handle(new String[] {"my-flag"}, LOCALE);

      verify(pendingToggleStore).store("my-flag", false);
    }

    @Test
    @DisplayName("stores true in pending store when flag is disabled")
    void handle_storesTrue_whenFlagIsDisabled() {
      FeatureFlagResponse disabledFlag =
          new FeatureFlagResponse("my-flag", false, null, null, null, null, null, null);
      when(envClient.getFlagByKey("my-flag")).thenReturn(disabledFlag);

      handler.handle(new String[] {"my-flag"}, LOCALE);

      verify(pendingToggleStore).store("my-flag", true);
    }

    @Test
    @DisplayName("returns error when client throws during confirmation prompt")
    void handle_returnsError_whenClientThrows() {
      when(envClient.getFlagByKey("bad-flag")).thenThrow(new RuntimeException("not found"));

      BotResponse result = handler.handle(new String[] {"bad-flag"}, LOCALE);

      assertThat(result.text()).contains("bad-flag");
      verify(pendingToggleStore, never())
          .store(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // handle — confirm
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handle — confirm")
  class HandleConfirm {

    @Test
    @DisplayName("executes toggle and returns success when pending state exists")
    void handle_executesToggle_whenPendingExists() {
      when(pendingToggleStore.get("dark-mode")).thenReturn(false);
      FeatureFlagResponse toggled =
          new FeatureFlagResponse("dark-mode", false, null, null, null, null, null, null);
      when(envClient.toggleFlag("dark-mode", false, "telegram-bot")).thenReturn(toggled);

      BotResponse result = handler.handle(new String[] {"dark-mode", "confirm"}, LOCALE);

      assertThat(result.text()).contains("dark-mode");
      assertThat(result.text()).contains("toggled");
      verify(envClient).toggleFlag("dark-mode", false, "telegram-bot");
      verify(pendingToggleStore).remove("dark-mode");
    }

    @Test
    @DisplayName("returns no-pending error when confirm is called with no stored state")
    void handle_returnsNoPending_whenNoStoredState() {
      when(pendingToggleStore.get("dark-mode")).thenReturn(null);

      BotResponse result = handler.handle(new String[] {"dark-mode", "confirm"}, LOCALE);

      assertThat(result.text()).contains("No pending toggle");
      assertThat(result.text()).contains("dark-mode");
      verify(envClient, never())
          .toggleFlag(
              org.mockito.ArgumentMatchers.any(),
              org.mockito.ArgumentMatchers.anyBoolean(),
              org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("returns error and removes pending when toggle client throws")
    void handle_returnsError_andRemovesPending_whenClientThrows() {
      when(pendingToggleStore.get("bad-flag")).thenReturn(true);
      when(envClient.toggleFlag("bad-flag", true, "telegram-bot"))
          .thenThrow(new RuntimeException("service error"));

      BotResponse result = handler.handle(new String[] {"bad-flag", "confirm"}, LOCALE);

      assertThat(result.text()).contains("Failed to toggle");
      assertThat(result.text()).contains("bad-flag");
      verify(pendingToggleStore).remove("bad-flag");
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // handle — cancel
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handle — cancel")
  class HandleCancel {

    @Test
    @DisplayName("removes pending entry and returns cancelled message")
    void handle_removesPending_andReturnsCancelled() {
      BotResponse result = handler.handle(new String[] {"dark-mode", "cancel"}, LOCALE);

      verify(pendingToggleStore).remove("dark-mode");
      assertThat(result.text()).contains("cancelled");
      assertThat(result.text()).contains("dark-mode");
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // handle — authorization
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handle — authorization")
  class HandleAuthorization {

    @Test
    @DisplayName("returns unauthorized when user lacks toggle permission")
    void handle_returnsUnauthorized_whenUserLacksPermission() {
      when(authService.canToggleFlags(42L)).thenReturn(false);

      BotResponse result = handler.handle(new String[] {"dark-mode", "__userId__42"}, LOCALE);

      assertThat(result.text()).contains("not authorized");
      verify(envClient, never()).getFlagByKey(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("proceeds when user has toggle permission")
    void handle_proceeds_whenUserHasPermission() {
      when(authService.canToggleFlags(42L)).thenReturn(true);
      FeatureFlagResponse flag =
          new FeatureFlagResponse("dark-mode", true, null, null, null, null, null, null);
      when(envClient.getFlagByKey("dark-mode")).thenReturn(flag);

      BotResponse result = handler.handle(new String[] {"dark-mode", "__userId__42"}, LOCALE);

      assertThat(result.text()).contains("dark-mode");
      verify(pendingToggleStore).store("dark-mode", false);
    }
  }
}
