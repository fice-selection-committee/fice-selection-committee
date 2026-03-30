package edu.kpi.fice.telegram_service.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.kpi.fice.sc.events.dto.AuditEventDto;
import edu.kpi.fice.telegram_service.domain.BotUser;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;

@ExtendWith(MockitoExtension.class)
@DisplayName("FlagChangeNotificationListener")
class FlagChangeNotificationListenerTest {

  @Mock private TelegramApiClient telegramClient;
  @Mock private BotUserService botUserService;

  private FlagChangeNotificationListener listener;

  @BeforeEach
  void setUp() {
    var source = new ResourceBundleMessageSource();
    source.setBasename("messages/messages");
    source.setDefaultEncoding("UTF-8");
    source.setUseCodeAsDefaultMessage(true);
    BotMessageResolver msg = new BotMessageResolver(source);
    listener = new FlagChangeNotificationListener(telegramClient, botUserService, msg);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // helpers
  // ─────────────────────────────────────────────────────────────────────────

  private static BotUser subscribedUser(Long userId, Long chatId, String lang) {
    return BotUser.builder()
        .telegramUserId(userId)
        .chatId(chatId)
        .languageCode(lang)
        .subscribed(true)
        .build();
  }

  private AuditEventDto auditEvent(String eventType, String objectId) {
    return new AuditEventDto(
        UUID.randomUUID(),
        "environment-service",
        "user-1",
        "USER",
        eventType,
        "FeatureFlag",
        objectId,
        null,
        Instant.now(),
        null);
  }

  private AuditEventDto auditEventWithPayload(
      String eventType, String objectId, Map<String, Object> payload) {
    return new AuditEventDto(
        UUID.randomUUID(),
        "environment-service",
        "user-1",
        "USER",
        eventType,
        "FeatureFlag",
        objectId,
        payload,
        Instant.now(),
        null);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // feature_flag events — delivery
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("feature_flag.created event")
  class FeatureFlagCreatedEvent {

    @Test
    @DisplayName("sends notification to all subscribed users")
    void created_sendsNotificationToAllSubscribedUsers() {
      when(botUserService.getSubscribedUsers())
          .thenReturn(
              List.of(
                  subscribedUser(1L, 100L, "en"),
                  subscribedUser(2L, 200L, "en"),
                  subscribedUser(3L, 300L, "uk")));
      AuditEventDto event = auditEvent("feature_flag.created", "dark-mode");

      listener.handleAuditEvent(event);

      verify(telegramClient, times(3)).sendMessage(anyLong(), anyString(), any());
      verify(telegramClient).sendMessage(eq(100L), anyString(), any());
      verify(telegramClient).sendMessage(eq(200L), anyString(), any());
      verify(telegramClient).sendMessage(eq(300L), anyString(), any());
    }

    @Test
    @DisplayName("notification message contains flag key")
    void created_notificationContainsFlagKey() {
      when(botUserService.getSubscribedUsers())
          .thenReturn(List.of(subscribedUser(1L, 42L, "en")));
      AuditEventDto event = auditEvent("feature_flag.created", "dark-mode");

      listener.handleAuditEvent(event);

      verify(telegramClient).sendMessage(eq(42L), contains("dark-mode"), any());
    }

    @Test
    @DisplayName("notification message contains Flag Created header")
    void created_notificationContainsCreatedHeader() {
      when(botUserService.getSubscribedUsers())
          .thenReturn(List.of(subscribedUser(1L, 42L, "en")));
      AuditEventDto event = auditEvent("feature_flag.created", "dark-mode");

      listener.handleAuditEvent(event);

      verify(telegramClient).sendMessage(eq(42L), contains("Flag Created"), any());
    }
  }

  @Nested
  @DisplayName("feature_flag.updated event")
  class FeatureFlagUpdatedEvent {

    @Test
    @DisplayName("sends notification to subscribed user")
    void updated_sendsNotification() {
      when(botUserService.getSubscribedUsers())
          .thenReturn(List.of(subscribedUser(1L, 55L, "en")));
      AuditEventDto event = auditEvent("feature_flag.updated", "my-flag");

      listener.handleAuditEvent(event);

      verify(telegramClient).sendMessage(eq(55L), contains("Flag Updated"), any());
    }
  }

  @Nested
  @DisplayName("feature_flag.deleted event")
  class FeatureFlagDeletedEvent {

    @Test
    @DisplayName("sends notification to subscribed user")
    void deleted_sendsNotification() {
      when(botUserService.getSubscribedUsers())
          .thenReturn(List.of(subscribedUser(1L, 77L, "en")));
      AuditEventDto event = auditEvent("feature_flag.deleted", "old-flag");

      listener.handleAuditEvent(event);

      verify(telegramClient).sendMessage(eq(77L), contains("Flag Deleted"), any());
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // status_changed — payload formatting
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("feature_flag.status_changed event")
  class StatusChangedEvent {

    @Test
    @DisplayName("formats old → new status transition in the notification")
    void statusChanged_formatsOldAndNewStatus() {
      when(botUserService.getSubscribedUsers())
          .thenReturn(List.of(subscribedUser(1L, 10L, "en")));
      AuditEventDto event =
          auditEventWithPayload(
              "feature_flag.status_changed",
              "payment-flag",
              Map.of("oldStatus", "INACTIVE", "newStatus", "ACTIVE"));

      listener.handleAuditEvent(event);

      verify(telegramClient).sendMessage(eq(10L), contains("INACTIVE"), any());
      verify(telegramClient).sendMessage(eq(10L), contains("ACTIVE"), any());
      verify(telegramClient).sendMessage(eq(10L), contains("→"), any());
    }

    @Test
    @DisplayName("includes enabled field from payload when present")
    void statusChanged_includesEnabledField_whenInPayload() {
      when(botUserService.getSubscribedUsers())
          .thenReturn(List.of(subscribedUser(1L, 10L, "en")));
      AuditEventDto event =
          auditEventWithPayload(
              "feature_flag.status_changed", "payment-flag", Map.of("enabled", true));

      listener.handleAuditEvent(event);

      verify(telegramClient).sendMessage(eq(10L), contains("Enabled"), any());
    }

    @Test
    @DisplayName("includes actor info in the notification")
    void created_includesActorInfo() {
      when(botUserService.getSubscribedUsers())
          .thenReturn(List.of(subscribedUser(1L, 10L, "en")));
      AuditEventDto event =
          new AuditEventDto(
              UUID.randomUUID(),
              "environment-service",
              "alice",
              "ADMIN",
              "feature_flag.created",
              "FeatureFlag",
              "some-flag",
              null,
              Instant.now(),
              null);

      listener.handleAuditEvent(event);

      verify(telegramClient).sendMessage(eq(10L), contains("alice"), any());
      verify(telegramClient).sendMessage(eq(10L), contains("ADMIN"), any());
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // filtering — non-feature-flag events
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("non-feature-flag events are ignored")
  class NonFeatureFlagEvents {

    @Test
    @DisplayName("ignores user.created audit event")
    void ignores_userCreatedEvent() {
      AuditEventDto event = auditEvent("user.created", "user-123");

      listener.handleAuditEvent(event);

      verify(telegramClient, never()).sendMessage(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("ignores document.uploaded audit event")
    void ignores_documentUploadedEvent() {
      AuditEventDto event = auditEvent("document.uploaded", "doc-456");

      listener.handleAuditEvent(event);

      verify(telegramClient, never()).sendMessage(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("ignores event with null eventType")
    void ignores_nullEventType() {
      AuditEventDto event =
          new AuditEventDto(
              UUID.randomUUID(), "svc", null, null, null, null, "obj-1", null, Instant.now(), null);

      listener.handleAuditEvent(event);

      verify(telegramClient, never()).sendMessage(anyLong(), anyString(), any());
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // null event
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("null event")
  class NullEvent {

    @Test
    @DisplayName("ignores null event without throwing")
    void ignores_nullEvent() {
      listener.handleAuditEvent(null);

      verify(telegramClient, never()).sendMessage(anyLong(), anyString(), any());
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // no subscribed users
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("no subscribed users")
  class NoSubscribedUsers {

    @Test
    @DisplayName("sends nothing when no users are subscribed")
    void sendsNothing_whenNoUsersSubscribed() {
      when(botUserService.getSubscribedUsers()).thenReturn(List.of());
      AuditEventDto event = auditEvent("feature_flag.created", "dark-mode");

      listener.handleAuditEvent(event);

      verify(telegramClient, never()).sendMessage(anyLong(), anyString(), any());
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // unknown feature_flag sub-type falls through to default branch
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("unknown feature_flag sub-event type")
  class UnknownFeatureFlagSubtype {

    @Test
    @DisplayName("still notifies subscribed users using the generic flag event header")
    void unknownSubtype_usesGenericHeader() {
      when(botUserService.getSubscribedUsers())
          .thenReturn(List.of(subscribedUser(1L, 99L, "en")));
      AuditEventDto event = auditEvent("feature_flag.archived", "legacy-flag");

      listener.handleAuditEvent(event);

      verify(telegramClient).sendMessage(eq(99L), contains("feature_flag.archived"), any());
    }
  }
}
