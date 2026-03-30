package edu.kpi.fice.telegram_service.service;

import edu.kpi.fice.sc.events.dto.AuditEventDto;
import edu.kpi.fice.telegram_service.config.RabbitConfig;
import edu.kpi.fice.telegram_service.domain.BotUser;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(TelegramApiClient.class)
public class FlagChangeNotificationListener {

  private final TelegramApiClient telegramClient;
  private final BotUserService botUserService;
  private final BotMessageResolver msg;

  @RabbitListener(queues = RabbitConfig.BOT_NOTIFICATIONS_QUEUE)
  public void handleAuditEvent(AuditEventDto event) {
    if (event == null || event.eventType() == null) {
      return;
    }

    if (!event.eventType().startsWith("feature_flag.")) {
      return;
    }

    log.info("Sending flag change notification: {}", event.eventType());

    // Send to DB-backed subscribed users with their locale and thread ID
    for (BotUser user : botUserService.getSubscribedUsers()) {
      Locale locale = BotMessageResolver.resolveLocale(user.getLanguageCode());
      String message = formatNotification(event, locale);
      telegramClient.sendMessage(user.getChatId(), message, user.getMessageThreadId());
    }
  }

  private String formatNotification(AuditEventDto event, Locale locale) {
    var sb = new StringBuilder();

    String eventTitle =
        switch (event.eventType()) {
          case "feature_flag.created" -> "\uD83C\uDD95 <b>"
              + msg.msg(locale, "notif.event.created")
              + "</b>";
          case "feature_flag.updated" -> "\u270F\uFE0F <b>"
              + msg.msg(locale, "notif.event.updated")
              + "</b>";
          case "feature_flag.deleted" -> "\uD83D\uDDD1 <b>"
              + msg.msg(locale, "notif.event.deleted")
              + "</b>";
          case "feature_flag.status_changed" -> "\uD83D\uDD04 <b>"
              + msg.msg(locale, "notif.event.status_changed")
              + "</b>";
          default -> "\uD83D\uDCCB <b>"
              + msg.msg(locale, "notif.event.unknown", event.eventType())
              + "</b>";
        };
    sb.append(eventTitle).append("\n");

    sb.append("\n<b>")
        .append(msg.msg(locale, "notif.event.field.flag"))
        .append(":</b> <code>")
        .append(event.objectId())
        .append("</code>\n");

    if (event.actorId() != null) {
      sb.append("<b>")
          .append(msg.msg(locale, "notif.event.field.by"))
          .append(":</b> ")
          .append(event.actorId());
      if (event.actorType() != null) {
        sb.append(" (").append(event.actorType()).append(")");
      }
      sb.append("\n");
    }

    if (event.payload() != null && !event.payload().isEmpty()) {
      if (event.payload().containsKey("enabled")) {
        sb.append("<b>")
            .append(msg.msg(locale, "notif.event.field.enabled"))
            .append(":</b> ")
            .append(event.payload().get("enabled"))
            .append("\n");
      }
      if (event.payload().containsKey("oldStatus") && event.payload().containsKey("newStatus")) {
        sb.append("<b>")
            .append(msg.msg(locale, "notif.event.field.status"))
            .append(":</b> ")
            .append(event.payload().get("oldStatus"))
            .append(" \u2192 ")
            .append(event.payload().get("newStatus"))
            .append("\n");
      }
    }

    return sb.toString();
  }
}
