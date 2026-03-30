package edu.kpi.fice.telegram_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommandMenuRegistrar")
class CommandMenuRegistrarTest {

  @Mock private TelegramApiClient telegramClient;

  @InjectMocks private CommandMenuRegistrar registrar;

  @Test
  @DisplayName("registerCommands calls setMyCommands with correct command list")
  void registerCommands_callsSetMyCommands() {
    registrar.registerCommands();

    verify(telegramClient).setMyCommands(CommandMenuRegistrar.COMMANDS);
  }

  @Test
  @DisplayName("COMMANDS list contains all expected bot commands")
  void commands_containsAllExpectedCommands() {
    assertThat(CommandMenuRegistrar.COMMANDS)
        .extracting(m -> m.get("command"))
        .containsExactly(
            "start",
            "flags",
            "flag",
            "search",
            "toggle",
            "subscribe",
            "unsubscribe",
            "settings",
            "help");
  }
}
