package edu.kpi.fice.telegram_service.service;

import edu.kpi.fice.telegram_service.dto.BotResponse;
import edu.kpi.fice.telegram_service.i18n.BotMessageResolver;
import edu.kpi.fice.telegram_service.service.handler.BotCommandHandler;
import edu.kpi.fice.telegram_service.service.handler.FlagsCallbackHandler;
import edu.kpi.fice.telegram_service.service.handler.MenuCallbackHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BotCommandDispatcher {

  private final List<BotCommandHandler> handlers;
  private final MenuCallbackHandler menuHandler;
  private final FlagsCallbackHandler flagsCallbackHandler;
  private final BotMessageResolver messageResolver;
  private final MeterRegistry meterRegistry;

  public BotCommandDispatcher(
      List<BotCommandHandler> handlers,
      MenuCallbackHandler menuHandler,
      FlagsCallbackHandler flagsCallbackHandler,
      BotMessageResolver messageResolver,
      MeterRegistry meterRegistry) {
    this.handlers = handlers;
    this.menuHandler = menuHandler;
    this.flagsCallbackHandler = flagsCallbackHandler;
    this.messageResolver = messageResolver;
    this.meterRegistry = meterRegistry;
  }

  public BotResponse dispatch(
      String text, Long chatId, Long userId, Locale locale, Integer messageThreadId) {
    if (text == null || text.isBlank()) {
      return buildHelpResponse(locale);
    }

    String[] parts = text.trim().split("\\s+");
    String command = parts[0].toLowerCase();

    // Strip bot username suffix (e.g., /flags@MyBot -> /flags)
    if (command.contains("@")) {
      command = command.substring(0, command.indexOf('@'));
    }

    if ("/start".equals(command)) {
      recordCommandMetric("/start", "success");
      // Handle deep links: /start flag_<key>
      if (parts.length > 1 && parts[1].startsWith("flag_")) {
        String flagKey = parts[1].substring("flag_".length());
        return flagsCallbackHandler.handle("flags:view:" + flagKey, userId, chatId, null, locale);
      }
      return menuHandler.buildMainMenu(locale, null);
    }

    if ("/help".equals(command)) {
      recordCommandMetric("/help", "success");
      return buildHelpResponse(locale);
    }

    // Build args — inject chatId/userId/messageThreadId for commands that need them
    String[] args;
    if (("/subscribe".equals(command) || "/unsubscribe".equals(command)) && chatId != null) {
      args =
          new String[] {
            String.valueOf(chatId),
            String.valueOf(userId),
            messageThreadId != null ? String.valueOf(messageThreadId) : ""
          };
    } else if ("/toggle".equals(command) && userId != null) {
      // Inject userId as last arg for authorization
      String[] cmdArgs = new String[parts.length - 1];
      System.arraycopy(parts, 1, cmdArgs, 0, cmdArgs.length);
      args = new String[cmdArgs.length + 1];
      System.arraycopy(cmdArgs, 0, args, 0, cmdArgs.length);
      args[args.length - 1] = "__userId__" + userId;
    } else {
      args = new String[parts.length - 1];
      System.arraycopy(parts, 1, args, 0, args.length);
    }

    for (BotCommandHandler handler : handlers) {
      if (handler.supports(command)) {
        log.info("Dispatching command '{}' to {}", command, handler.getClass().getSimpleName());
        try {
          BotResponse result = handler.handle(args, locale);
          recordCommandMetric(command, "success");
          return result;
        } catch (Exception e) {
          recordCommandMetric(command, "error");
          throw e;
        }
      }
    }

    recordCommandMetric(command, "unknown");
    return BotResponse.text(
        messageResolver.msg(locale, "common.unknown_command", "<code>" + command + "</code>"));
  }

  private BotResponse buildHelpResponse(Locale locale) {
    var sb = new StringBuilder("<b>" + messageResolver.msg(locale, "help.title") + "</b>\n\n");
    sb.append(messageResolver.msg(locale, "help.cmd.flags")).append("\n");
    sb.append(messageResolver.msg(locale, "help.cmd.flag")).append("\n");
    sb.append(messageResolver.msg(locale, "help.cmd.search")).append("\n");
    sb.append(messageResolver.msg(locale, "help.cmd.subscribe")).append("\n");
    sb.append(messageResolver.msg(locale, "help.cmd.unsubscribe")).append("\n");
    sb.append(messageResolver.msg(locale, "help.cmd.toggle")).append("\n");
    sb.append(messageResolver.msg(locale, "help.cmd.settings")).append("\n");
    sb.append(messageResolver.msg(locale, "help.cmd.help"));
    return BotResponse.text(sb.toString());
  }

  private void recordCommandMetric(String command, String status) {
    Counter.builder("bot.commands.total")
        .tag("command", command)
        .tag("status", status)
        .register(meterRegistry)
        .increment();
  }
}
