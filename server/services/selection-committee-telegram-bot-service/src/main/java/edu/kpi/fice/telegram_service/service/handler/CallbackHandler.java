package edu.kpi.fice.telegram_service.service.handler;

import edu.kpi.fice.telegram_service.dto.BotResponse;
import java.util.Locale;

/** Handler for inline keyboard callback queries. */
public interface CallbackHandler {

  /**
   * Returns true if this handler can process the given callback data prefix.
   *
   * @param module the module prefix from the callback data (e.g., "menu", "flags", "settings")
   */
  boolean supports(String module);

  /**
   * Handles a callback query.
   *
   * @param callbackData full callback data string (e.g., "flags:list:0")
   * @param userId the Telegram user ID
   * @param chatId the chat ID
   * @param messageId the message ID to edit
   * @param locale the user's locale
   * @return a BotResponse to send back
   */
  BotResponse handle(
      String callbackData, Long userId, Long chatId, Integer messageId, Locale locale);
}
