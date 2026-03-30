package edu.kpi.fice.telegram_service.service.handler;

import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import edu.kpi.fice.telegram_service.service.BotUserService;
import edu.kpi.fice.telegram_service.service.NotificationChatRegistry;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UnsubscribeHandler implements BotCommandHandler {

  private final NotificationChatRegistry chatRegistry;
  private final BotUserService botUserService;
  private final BotMessageResolver msg;

  @Override
  public boolean supports(String command) {
    return "/unsubscribe".equals(command);
  }

  @Override
  public BotResponse handle(String[] args, Locale locale) {
    if (args.length < 2) {
      return BotResponse.text(msg.msg(locale, "common.error.internal"));
    }
    long chatId = Long.parseLong(args[0]);
    long userId = Long.parseLong(args[1]);

    // Unsubscribe via DB (primary)
    if (!botUserService.isSubscribed(userId)) {
      return BotResponse.text(msg.msg(locale, "notif.unsubscribe.not_subscribed"));
    }
    botUserService.toggleSubscription(userId);

    // Also unsubscribe from legacy file registry
    chatRegistry.unsubscribe(chatId);

    return BotResponse.text(msg.msg(locale, "notif.unsubscribe.success"));
  }
}
