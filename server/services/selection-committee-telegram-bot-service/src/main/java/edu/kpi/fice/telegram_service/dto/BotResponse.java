package edu.kpi.fice.telegram_service.dto;

import java.util.List;

/**
 * Represents a structured bot response that can include text and an optional inline keyboard.
 *
 * @param text HTML-formatted message text
 * @param keyboard optional inline keyboard rows (null = no keyboard)
 * @param editMessageId if set, edit this message instead of sending a new one
 */
public record BotResponse(String text, List<List<InlineButton>> keyboard, Integer editMessageId) {

  public record InlineButton(String text, String callbackData) {}

  public static BotResponse text(String text) {
    return new BotResponse(text, null, null);
  }

  public static BotResponse withKeyboard(String text, List<List<InlineButton>> keyboard) {
    return new BotResponse(text, keyboard, null);
  }

  public static BotResponse edit(
      String text, List<List<InlineButton>> keyboard, Integer messageId) {
    return new BotResponse(text, keyboard, messageId);
  }

  public boolean hasKeyboard() {
    return keyboard != null && !keyboard.isEmpty();
  }

  public boolean isEdit() {
    return editMessageId != null;
  }
}
