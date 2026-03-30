package edu.kpi.fice.telegram_service.service.handler;

import edu.kpi.fice.telegram_service.dto.BotResponse;
import java.util.Locale;

public interface BotCommandHandler {

  boolean supports(String command);

  BotResponse handle(String[] args, Locale locale);
}
