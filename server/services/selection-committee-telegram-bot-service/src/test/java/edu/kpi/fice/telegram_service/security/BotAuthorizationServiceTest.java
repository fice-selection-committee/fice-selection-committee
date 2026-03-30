package edu.kpi.fice.telegram_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import edu.kpi.fice.telegram_service.domain.BotUser;
import edu.kpi.fice.telegram_service.repository.BotUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BotAuthorizationService")
class BotAuthorizationServiceTest {

  @Mock private BotUserRepository userRepository;

  private BotAuthorizationService authService;

  @BeforeEach
  void setUp() {
    authService = new BotAuthorizationService(userRepository);
  }

  @Nested
  @DisplayName("canToggleFlags")
  class CanToggleFlags {

    @Test
    @DisplayName("returns true for admin role")
    void canToggleFlags_returnsTrue_forAdmin() {
      BotUser user = BotUser.builder().telegramUserId(1L).chatId(10L).role("admin").build();
      when(userRepository.findById(1L)).thenReturn(Optional.of(user));

      assertThat(authService.canToggleFlags(1L)).isTrue();
    }

    @Test
    @DisplayName("returns true for operator role")
    void canToggleFlags_returnsTrue_forOperator() {
      BotUser user = BotUser.builder().telegramUserId(2L).chatId(20L).role("operator").build();
      when(userRepository.findById(2L)).thenReturn(Optional.of(user));

      assertThat(authService.canToggleFlags(2L)).isTrue();
    }

    @Test
    @DisplayName("returns false for viewer role")
    void canToggleFlags_returnsFalse_forViewer() {
      BotUser user = BotUser.builder().telegramUserId(3L).chatId(30L).role("viewer").build();
      when(userRepository.findById(3L)).thenReturn(Optional.of(user));

      assertThat(authService.canToggleFlags(3L)).isFalse();
    }

    @Test
    @DisplayName("returns false when user does not exist")
    void canToggleFlags_returnsFalse_whenUserNotFound() {
      when(userRepository.findById(99L)).thenReturn(Optional.empty());

      assertThat(authService.canToggleFlags(99L)).isFalse();
    }
  }

  @Nested
  @DisplayName("canViewAudit")
  class CanViewAudit {

    @Test
    @DisplayName("returns true for admin role")
    void canViewAudit_returnsTrue_forAdmin() {
      BotUser user = BotUser.builder().telegramUserId(1L).chatId(10L).role("admin").build();
      when(userRepository.findById(1L)).thenReturn(Optional.of(user));

      assertThat(authService.canViewAudit(1L)).isTrue();
    }

    @Test
    @DisplayName("returns true for operator role")
    void canViewAudit_returnsTrue_forOperator() {
      BotUser user = BotUser.builder().telegramUserId(2L).chatId(20L).role("operator").build();
      when(userRepository.findById(2L)).thenReturn(Optional.of(user));

      assertThat(authService.canViewAudit(2L)).isTrue();
    }

    @Test
    @DisplayName("returns false for viewer role")
    void canViewAudit_returnsFalse_forViewer() {
      BotUser user = BotUser.builder().telegramUserId(3L).chatId(30L).role("viewer").build();
      when(userRepository.findById(3L)).thenReturn(Optional.of(user));

      assertThat(authService.canViewAudit(3L)).isFalse();
    }
  }
}
