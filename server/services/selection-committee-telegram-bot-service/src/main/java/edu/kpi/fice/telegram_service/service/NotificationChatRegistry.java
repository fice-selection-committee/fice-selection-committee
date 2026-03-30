package edu.kpi.fice.telegram_service.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * File-backed registry of Telegram chats subscribed to flag change notifications. Persists
 * subscriptions to a JSON file so they survive restarts. Each chat ID is paired with an optional
 * message thread ID (null = General topic / no threads).
 */
@Slf4j
@Component
public class NotificationChatRegistry {

  private final Map<Long, Integer> subscribedChats = Collections.synchronizedMap(new HashMap<>());
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Path storePath;

  public NotificationChatRegistry(
      @Value("${telegram.subscriptions.file:data/subscriptions.json}") String filePath) {
    this.storePath = Path.of(filePath);
  }

  @PostConstruct
  void loadFromDisk() {
    if (Files.exists(storePath)) {
      try {
        // Try loading as Map<Long, Integer> (new format)
        Map<Long, Integer> loaded =
            objectMapper.readValue(storePath.toFile(), new TypeReference<>() {});
        subscribedChats.putAll(loaded);
        log.info("Loaded {} chat subscriptions from {}", loaded.size(), storePath);
      } catch (IOException e) {
        // Attempt legacy Set<Long> format migration
        try {
          Set<Long> legacy =
              objectMapper.readValue(storePath.toFile(), new TypeReference<>() {});
          for (Long chatId : legacy) {
            subscribedChats.put(chatId, null);
          }
          log.info(
              "Migrated {} legacy chat subscriptions from {}", legacy.size(), storePath);
          persistToDisk();
        } catch (IOException ex) {
          log.warn("Failed to load subscriptions from {}: {}", storePath, ex.getMessage());
        }
      }
    }
  }

  public void subscribe(Long chatId, Integer messageThreadId) {
    subscribedChats.put(chatId, messageThreadId);
    log.info(
        "Chat {} subscribed to flag notifications (threadId={})", chatId, messageThreadId);
    persistToDisk();
  }

  public void unsubscribe(Long chatId) {
    subscribedChats.remove(chatId);
    log.info("Chat {} unsubscribed from flag notifications", chatId);
    persistToDisk();
  }

  public boolean isSubscribed(Long chatId) {
    return subscribedChats.containsKey(chatId);
  }

  public Set<Long> getSubscribedChats() {
    return Set.copyOf(subscribedChats.keySet());
  }

  public Integer getThreadId(Long chatId) {
    return subscribedChats.get(chatId);
  }

  public Map<Long, Integer> getSubscriptions() {
    return Map.copyOf(subscribedChats);
  }

  private void persistToDisk() {
    try {
      Files.createDirectories(storePath.getParent());
      objectMapper.writeValue(storePath.toFile(), subscribedChats);
    } catch (IOException e) {
      log.error("Failed to persist subscriptions to {}: {}", storePath, e.getMessage());
    }
  }
}
