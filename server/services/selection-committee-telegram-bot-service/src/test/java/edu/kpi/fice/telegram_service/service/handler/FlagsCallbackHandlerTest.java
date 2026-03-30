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
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;

@ExtendWith(MockitoExtension.class)
@DisplayName("FlagsCallbackHandler")
class FlagsCallbackHandlerTest {

  private static final Locale LOCALE = Locale.ENGLISH;
  private static final Long USER_ID = 1L;
  private static final Long CHAT_ID = 10L;
  private static final Integer MESSAGE_ID = 99;

  @Mock private EnvironmentServiceClient envClient;
  @Mock private PendingToggleStore pendingToggleStore;
  @Mock private BotAuthorizationService authService;
  @Mock private BotUserService botUserService;

  private FlagsCallbackHandler handler;

  @BeforeEach
  void setUp() {
    var source = new ResourceBundleMessageSource();
    source.setBasename("messages/messages");
    source.setDefaultEncoding("UTF-8");
    source.setUseCodeAsDefaultMessage(true);
    BotMessageResolver msg = new BotMessageResolver(source);
    handler =
        new FlagsCallbackHandler(envClient, pendingToggleStore, msg, authService, botUserService);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // supports
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("supports")
  class Supports {

    @Test
    @DisplayName("returns true for 'flags' module")
    void supportsFlags() {
      assertThat(handler.supports("flags")).isTrue();
    }

    @Test
    @DisplayName("returns false for 'menu'")
    void doesNotSupportMenu() {
      assertThat(handler.supports("menu")).isFalse();
    }

    @Test
    @DisplayName("returns false for 'settings'")
    void doesNotSupportSettings() {
      assertThat(handler.supports("settings")).isFalse();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // handle — list
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handle — list")
  class HandleList {

    @Test
    @DisplayName("renders flag list with page 0 when no page is specified")
    void handle_rendersList_withPageZero() {
      FeatureFlagResponse flag =
          new FeatureFlagResponse("dark-mode", true, null, null, null, null, null, null);
      when(envClient.getFlags(null, null)).thenReturn(List.of(flag));

      BotResponse result = handler.handle("flags:list", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("dark-mode");
    }

    @Test
    @DisplayName("renders empty message when no flags exist")
    void handle_rendersEmptyMessage_whenNoFlags() {
      when(envClient.getFlags(null, null)).thenReturn(List.of());

      BotResponse result = handler.handle("flags:list:0", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("No feature flags");
    }

    @Test
    @DisplayName("renders error message when client throws")
    void handle_rendersError_whenClientThrows() {
      when(envClient.getFlags(null, null)).thenThrow(new RuntimeException("service down"));

      BotResponse result = handler.handle("flags:list:0", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("Failed to fetch");
    }

    @Test
    @DisplayName("renders pagination buttons when total flags exceed page size")
    void handle_rendersPagination_whenFlagsExceedPageSize() {
      List<FeatureFlagResponse> flags =
          List.of(
              new FeatureFlagResponse("f1", true, null, null, null, null, null, null),
              new FeatureFlagResponse("f2", false, null, null, null, null, null, null),
              new FeatureFlagResponse("f3", true, null, null, null, null, null, null),
              new FeatureFlagResponse("f4", false, null, null, null, null, null, null),
              new FeatureFlagResponse("f5", true, null, null, null, null, null, null),
              new FeatureFlagResponse("f6", false, null, null, null, null, null, null));
      when(envClient.getFlags(null, null)).thenReturn(flags);

      BotResponse result = handler.handle("flags:list:0", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      // Next page button should be present
      assertThat(result.keyboard())
          .anyMatch(row -> row.stream().anyMatch(b -> b.callbackData().equals("flags:list:1")));
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // handle — view
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handle — view")
  class HandleView {

    @Test
    @DisplayName("renders flag detail for existing flag")
    void handle_rendersFlagDetail_whenFlagExists() {
      FeatureFlagResponse flag =
          new FeatureFlagResponse(
              "dark-mode", true, "Dark theme", "RELEASE", "ui-team", "ACTIVE", 100, null);
      when(envClient.getFlagByKey("dark-mode")).thenReturn(flag);

      BotResponse result =
          handler.handle("flags:view:dark-mode", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("dark-mode");
    }

    @Test
    @DisplayName("toggle button uses localized text instead of hardcoded 'Toggle'")
    void handle_toggleButton_usesLocalizedText() {
      FeatureFlagResponse flag =
          new FeatureFlagResponse("dark-mode", true, null, null, null, null, null, null);
      when(envClient.getFlagByKey("dark-mode")).thenReturn(flag);

      BotResponse result =
          handler.handle("flags:view:dark-mode", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      // The toggle button should show the localized "Toggle" text from i18n
      assertThat(result.keyboard())
          .anyMatch(
              row ->
                  row.stream()
                      .anyMatch(
                          b ->
                              b.callbackData().equals("flags:toggle:dark-mode")
                                  && b.text().contains("Toggle")));
    }

    @Test
    @DisplayName("renders error message when flag is not found")
    void handle_rendersError_whenFlagNotFound() {
      when(envClient.getFlagByKey("missing")).thenThrow(new RuntimeException("not found"));

      BotResponse result =
          handler.handle("flags:view:missing", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("missing");
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // handle — toggle prompt
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handle — toggle prompt")
  class HandleTogglePrompt {

    @Test
    @DisplayName("stores pending toggle and renders confirmation prompt")
    void handle_storesPendingAndRendersPrompt() {
      when(authService.canToggleFlags(USER_ID)).thenReturn(true);
      FeatureFlagResponse flag =
          new FeatureFlagResponse("dark-mode", true, null, null, null, null, null, null);
      when(envClient.getFlagByKey("dark-mode")).thenReturn(flag);

      BotResponse result =
          handler.handle("flags:toggle:dark-mode", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      verify(pendingToggleStore).store("dark-mode", false);
      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("dark-mode");
      assertThat(result.text()).contains("Toggle Confirmation");
    }

    @Test
    @DisplayName("renders unauthorized when user lacks toggle permission")
    void handle_rendersUnauthorized_whenUserLacksPermission() {
      when(authService.canToggleFlags(USER_ID)).thenReturn(false);

      BotResponse result =
          handler.handle("flags:toggle:dark-mode", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("not authorized");
      verify(envClient, never()).getFlagByKey(ArgumentMatchers.any());
    }

    @Test
    @DisplayName("renders error when client throws during toggle prompt")
    void handle_rendersError_whenClientThrows() {
      when(authService.canToggleFlags(USER_ID)).thenReturn(true);
      when(envClient.getFlagByKey("bad")).thenThrow(new RuntimeException("timeout"));

      BotResponse result = handler.handle("flags:toggle:bad", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.isEdit()).isTrue();
      verify(pendingToggleStore, never())
          .store(ArgumentMatchers.any(), ArgumentMatchers.anyBoolean());
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // handle — toggle confirm
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handle — toggle confirm")
  class HandleToggleConfirm {

    @Test
    @DisplayName("executes toggle and renders success when pending state exists")
    void handle_executesToggle_whenPendingExists() {
      when(authService.canToggleFlags(USER_ID)).thenReturn(true);
      when(pendingToggleStore.get("dark-mode")).thenReturn(false);
      when(botUserService.getDisplayName(USER_ID)).thenReturn("@testuser");
      when(envClient.toggleFlag("dark-mode", false, "@testuser"))
          .thenReturn(
              new FeatureFlagResponse("dark-mode", false, null, null, null, null, null, null));

      BotResponse result =
          handler.handle("flags:toggle_confirm:dark-mode", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      verify(envClient).toggleFlag("dark-mode", false, "@testuser");
      verify(pendingToggleStore).remove("dark-mode");
      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("dark-mode");
    }

    @Test
    @DisplayName("renders no-pending error when no pending state exists")
    void handle_rendersNoPending_whenNoPendingState() {
      when(authService.canToggleFlags(USER_ID)).thenReturn(true);
      when(pendingToggleStore.get("dark-mode")).thenReturn(null);

      BotResponse result =
          handler.handle("flags:toggle_confirm:dark-mode", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("No pending toggle");
      verify(envClient, never())
          .toggleFlag(
              ArgumentMatchers.any(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.any());
    }

    @Test
    @DisplayName("renders error and removes pending when client throws")
    void handle_rendersError_andRemovesPending_whenClientThrows() {
      when(authService.canToggleFlags(USER_ID)).thenReturn(true);
      when(pendingToggleStore.get("bad")).thenReturn(true);
      when(envClient.toggleFlag("bad", true, "telegram-bot"))
          .thenThrow(new RuntimeException("service error"));

      BotResponse result =
          handler.handle("flags:toggle_confirm:bad", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("Failed to toggle");
      verify(pendingToggleStore).remove("bad");
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // handle — toggle cancel
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handle — toggle cancel")
  class HandleToggleCancel {

    @Test
    @DisplayName("removes pending entry and renders cancelled message")
    void handle_removesPendingAndRendersCancelled() {
      BotResponse result =
          handler.handle("flags:toggle_cancel:dark-mode", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      verify(pendingToggleStore).remove("dark-mode");
      assertThat(result.isEdit()).isTrue();
      assertThat(result.text()).contains("cancelled");
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // handle — unknown action
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("handle — unknown action")
  class HandleUnknownAction {

    @Test
    @DisplayName("returns generic error for unknown action")
    void handle_returnsGenericError_forUnknownAction() {
      BotResponse result =
          handler.handle("flags:unknown_action", USER_ID, CHAT_ID, MESSAGE_ID, LOCALE);

      assertThat(result.text()).contains("Something went wrong");
    }
  }
}
