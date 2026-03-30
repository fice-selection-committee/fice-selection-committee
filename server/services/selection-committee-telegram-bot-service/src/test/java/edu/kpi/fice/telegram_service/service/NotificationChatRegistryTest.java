package edu.kpi.fice.telegram_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("NotificationChatRegistry")
class NotificationChatRegistryTest {

  @TempDir Path tempDir;

  private Path storePath;
  private NotificationChatRegistry registry;

  @BeforeEach
  void setUp() {
    storePath = tempDir.resolve("subscriptions.json");
    registry = new NotificationChatRegistry(storePath.toString());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // subscribe
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("subscribe")
  class Subscribe {

    @Test
    @DisplayName("isSubscribed returns true after subscribing")
    void subscribe_makesIsSubscribedReturnTrue() {
      registry.subscribe(100L, null);

      assertThat(registry.isSubscribed(100L)).isTrue();
    }

    @Test
    @DisplayName("subscribing twice does not duplicate the entry")
    void subscribe_twice_doesNotDuplicate() {
      registry.subscribe(100L, null);
      registry.subscribe(100L, null);

      assertThat(registry.getSubscribedChats()).hasSize(1);
    }

    @Test
    @DisplayName("persists subscription to disk after subscribing")
    void subscribe_persistsToDisk() {
      registry.subscribe(200L, 3224);

      assertThat(storePath).exists();
    }

    @Test
    @DisplayName("stores thread ID paired with chat ID")
    void subscribe_storesThreadId() {
      registry.subscribe(100L, 3224);

      assertThat(registry.getThreadId(100L)).isEqualTo(3224);
    }

    @Test
    @DisplayName("stores null thread ID for General topic")
    void subscribe_storesNullThreadIdForGeneral() {
      registry.subscribe(100L, null);

      assertThat(registry.getThreadId(100L)).isNull();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // unsubscribe
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("unsubscribe")
  class Unsubscribe {

    @Test
    @DisplayName("isSubscribed returns false after unsubscribing")
    void unsubscribe_makesIsSubscribedReturnFalse() {
      registry.subscribe(100L, 3224);
      registry.unsubscribe(100L);

      assertThat(registry.isSubscribed(100L)).isFalse();
    }

    @Test
    @DisplayName("unsubscribing a non-subscribed chat does not throw")
    void unsubscribe_nonSubscribed_doesNotThrow() {
      registry.unsubscribe(999L);

      assertThat(registry.isSubscribed(999L)).isFalse();
    }

    @Test
    @DisplayName("persists updated state to disk after unsubscribing")
    void unsubscribe_persistsToDisk() {
      registry.subscribe(300L, null);
      registry.unsubscribe(300L);

      assertThat(storePath).exists();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // isSubscribed
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("isSubscribed")
  class IsSubscribed {

    @Test
    @DisplayName("returns false for a chat that was never subscribed")
    void isSubscribed_returnsFalse_forUnknownChat() {
      assertThat(registry.isSubscribed(42L)).isFalse();
    }

    @Test
    @DisplayName("returns true for a subscribed chat")
    void isSubscribed_returnsTrue_forSubscribedChat() {
      registry.subscribe(42L, null);

      assertThat(registry.isSubscribed(42L)).isTrue();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // getSubscribedChats
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("getSubscribedChats")
  class GetSubscribedChats {

    @Test
    @DisplayName("returns empty set when no chats are subscribed")
    void getSubscribedChats_returnsEmpty_whenNoSubscriptions() {
      assertThat(registry.getSubscribedChats()).isEmpty();
    }

    @Test
    @DisplayName("returns all subscribed chat IDs")
    void getSubscribedChats_returnsAllSubscribedIds() {
      registry.subscribe(1L, null);
      registry.subscribe(2L, 3224);
      registry.subscribe(3L, null);

      Set<Long> result = registry.getSubscribedChats();

      assertThat(result).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    @DisplayName("returned set is a snapshot and does not reflect subsequent mutations")
    void getSubscribedChats_returnsSnapshot() {
      registry.subscribe(10L, null);
      Set<Long> snapshot = registry.getSubscribedChats();
      registry.subscribe(20L, null);

      assertThat(snapshot).containsExactly(10L);
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // persistence — loadFromDisk
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("persistence")
  class Persistence {

    @Test
    @DisplayName("loads subscriptions from disk when file already exists (map format)")
    void loadFromDisk_loadsExistingSubscriptions() throws IOException {
      ObjectMapper mapper = new ObjectMapper();
      mapper.writeValue(storePath.toFile(), Map.of(111L, 3224, 222L, 5678));

      NotificationChatRegistry freshRegistry = new NotificationChatRegistry(storePath.toString());
      freshRegistry.loadFromDisk();

      assertThat(freshRegistry.isSubscribed(111L)).isTrue();
      assertThat(freshRegistry.isSubscribed(222L)).isTrue();
      assertThat(freshRegistry.getThreadId(111L)).isEqualTo(3224);
      assertThat(freshRegistry.getThreadId(222L)).isEqualTo(5678);
    }

    @Test
    @DisplayName("migrates legacy Set<Long> format to Map format")
    void loadFromDisk_migratesLegacySetFormat() throws IOException {
      ObjectMapper mapper = new ObjectMapper();
      mapper.writeValue(storePath.toFile(), Set.of(111L, 222L));

      NotificationChatRegistry freshRegistry = new NotificationChatRegistry(storePath.toString());
      freshRegistry.loadFromDisk();

      assertThat(freshRegistry.isSubscribed(111L)).isTrue();
      assertThat(freshRegistry.isSubscribed(222L)).isTrue();
      assertThat(freshRegistry.getThreadId(111L)).isNull();
      assertThat(freshRegistry.getThreadId(222L)).isNull();
    }

    @Test
    @DisplayName("starts with empty subscriptions when no file exists on disk")
    void loadFromDisk_startsEmpty_whenNoFileExists() {
      registry.loadFromDisk();

      assertThat(registry.getSubscribedChats()).isEmpty();
    }

    @Test
    @DisplayName("survives a corrupted disk file without throwing during loadFromDisk")
    void loadFromDisk_survivesCorruptedFile() throws IOException {
      Files.writeString(storePath, "not valid json {{ }}");

      NotificationChatRegistry freshRegistry = new NotificationChatRegistry(storePath.toString());
      freshRegistry.loadFromDisk();

      assertThat(freshRegistry.getSubscribedChats()).isEmpty();
    }
  }
}
