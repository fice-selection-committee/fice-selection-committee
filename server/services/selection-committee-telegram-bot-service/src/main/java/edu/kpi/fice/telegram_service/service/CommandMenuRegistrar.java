package edu.kpi.fice.telegram_service.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Registers the bot's slash commands with Telegram on application startup via the setMyCommands
 * API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(TelegramApiClient.class)
public class CommandMenuRegistrar {

  private final TelegramApiClient telegramClient;

  static final List<Map<String, String>> COMMANDS =
      List.of(
          Map.of("command", "start", "description", "Main menu"),
          Map.of("command", "flags", "description", "List feature flags"),
          Map.of("command", "flag", "description", "View flag details"),
          Map.of("command", "search", "description", "Search flags"),
          Map.of("command", "toggle", "description", "Toggle a flag"),
          Map.of("command", "subscribe", "description", "Subscribe to notifications"),
          Map.of("command", "unsubscribe", "description", "Unsubscribe from notifications"),
          Map.of("command", "settings", "description", "Bot settings"),
          Map.of("command", "help", "description", "Show help"));

  @EventListener(ApplicationReadyEvent.class)
  public void registerCommands() {
    telegramClient.setMyCommands(COMMANDS);
  }
}
