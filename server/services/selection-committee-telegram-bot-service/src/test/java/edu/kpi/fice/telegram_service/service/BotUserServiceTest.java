package edu.kpi.fice.telegram_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.kpi.fice.telegram_service.domain.BotUser;
import edu.kpi.fice.telegram_service.repository.BotUserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BotUserService")
class BotUserServiceTest {

  @Mock private BotUserRepository userRepository;

  private BotUserService service;

  @BeforeEach
  void setUp() {
    service = new BotUserService(userRepository, new SimpleMeterRegistry());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // resolveUser
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("resolveUser")
  class ResolveUser {

    @Test
    @DisplayName("creates a new user when user does not exist")
    void resolveUser_createsNewUser_whenUserDoesNotExist() {
      when(userRepository.findById(1L)).thenReturn(Optional.empty());
      BotUser saved = BotUser.builder().telegramUserId(1L).chatId(10L).languageCode("en").build();
      when(userRepository.save(any())).thenReturn(saved);

      BotUser result = service.resolveUser(1L, 10L, "en", null, null);

      assertThat(result.getTelegramUserId()).isEqualTo(1L);
      assertThat(result.getChatId()).isEqualTo(10L);
      assertThat(result.getLanguageCode()).isEqualTo("en");
      verify(userRepository).save(any());
    }

    @Test
    @DisplayName("maps language code 'uk' to Ukrainian locale on new user")
    void resolveUser_mapsUkToUkrainian_onNewUser() {
      when(userRepository.findById(2L)).thenReturn(Optional.empty());
      BotUser saved = BotUser.builder().telegramUserId(2L).chatId(20L).languageCode("uk").build();
      when(userRepository.save(any())).thenReturn(saved);

      BotUser result = service.resolveUser(2L, 20L, "uk", null, null);

      assertThat(result.getLanguageCode()).isEqualTo("uk");
    }

    @Test
    @DisplayName("defaults to English when telegram language code is null")
    void resolveUser_defaultsToEnglish_whenLanguageCodeIsNull() {
      when(userRepository.findById(3L)).thenReturn(Optional.empty());
      BotUser saved = BotUser.builder().telegramUserId(3L).chatId(30L).languageCode("en").build();
      when(userRepository.save(any())).thenReturn(saved);

      BotUser result = service.resolveUser(3L, 30L, null, null, null);

      assertThat(result.getLanguageCode()).isEqualTo("en");
    }

    @Test
    @DisplayName("updates chatId and touches existing user when user already exists")
    void resolveUser_updatesExistingUser_whenUserExists() {
      BotUser existing =
          BotUser.builder().telegramUserId(4L).chatId(40L).languageCode("en").build();
      when(userRepository.findById(4L)).thenReturn(Optional.of(existing));
      when(userRepository.save(existing)).thenReturn(existing);

      BotUser result = service.resolveUser(4L, 99L, "en", null, null);

      assertThat(result.getChatId()).isEqualTo(99L);
      verify(userRepository).save(existing);
    }

    @Test
    @DisplayName("maps language code 'ua' to Ukrainian locale on new user")
    void resolveUser_mapsUaToUkrainian_onNewUser() {
      when(userRepository.findById(5L)).thenReturn(Optional.empty());
      BotUser saved = BotUser.builder().telegramUserId(5L).chatId(50L).languageCode("uk").build();
      when(userRepository.save(any())).thenReturn(saved);

      BotUser result = service.resolveUser(5L, 50L, "ua", null, null);

      assertThat(result.getLanguageCode()).isEqualTo("uk");
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // updateLanguage
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("updateLanguage")
  class UpdateLanguage {

    @Test
    @DisplayName("updates language code on existing user")
    void updateLanguage_updatesLanguageCode() {
      BotUser user = BotUser.builder().telegramUserId(1L).chatId(10L).languageCode("en").build();
      when(userRepository.findById(1L)).thenReturn(Optional.of(user));
      when(userRepository.save(user)).thenReturn(user);

      BotUser result = service.updateLanguage(1L, "uk");

      assertThat(result.getLanguageCode()).isEqualTo("uk");
      verify(userRepository).save(user);
    }

    @Test
    @DisplayName("throws IllegalStateException when user does not exist")
    void updateLanguage_throws_whenUserDoesNotExist() {
      when(userRepository.findById(99L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.updateLanguage(99L, "uk"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("99");
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // toggleSubscription
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("toggleSubscription")
  class ToggleSubscription {

    @Test
    @DisplayName("sets subscribed=true when user was not subscribed")
    void toggleSubscription_setsTrue_whenPreviouslyFalse() {
      BotUser user = BotUser.builder().telegramUserId(1L).chatId(10L).subscribed(false).build();
      when(userRepository.findById(1L)).thenReturn(Optional.of(user));
      when(userRepository.save(user)).thenReturn(user);

      BotUser result = service.toggleSubscription(1L);

      assertThat(result.getSubscribed()).isTrue();
    }

    @Test
    @DisplayName("sets subscribed=false when user was subscribed")
    void toggleSubscription_setsFalse_whenPreviouslyTrue() {
      BotUser user = BotUser.builder().telegramUserId(2L).chatId(20L).subscribed(true).build();
      when(userRepository.findById(2L)).thenReturn(Optional.of(user));
      when(userRepository.save(user)).thenReturn(user);

      BotUser result = service.toggleSubscription(2L);

      assertThat(result.getSubscribed()).isFalse();
    }

    @Test
    @DisplayName("throws IllegalStateException when user does not exist")
    void toggleSubscription_throws_whenUserDoesNotExist() {
      when(userRepository.findById(99L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.toggleSubscription(99L))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("99");
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // getSubscribedUsers
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("getSubscribedUsers")
  class GetSubscribedUsers {

    @Test
    @DisplayName("returns all users with subscribed=true")
    void getSubscribedUsers_returnsSubscribedUsers() {
      BotUser u1 = BotUser.builder().telegramUserId(1L).chatId(10L).subscribed(true).build();
      BotUser u2 = BotUser.builder().telegramUserId(2L).chatId(20L).subscribed(true).build();
      when(userRepository.findAllBySubscribedTrue()).thenReturn(List.of(u1, u2));

      List<BotUser> result = service.getSubscribedUsers();

      assertThat(result).hasSize(2);
      assertThat(result).containsExactly(u1, u2);
    }

    @Test
    @DisplayName("returns empty list when no users are subscribed")
    void getSubscribedUsers_returnsEmpty_whenNoSubscribers() {
      when(userRepository.findAllBySubscribedTrue()).thenReturn(List.of());

      List<BotUser> result = service.getSubscribedUsers();

      assertThat(result).isEmpty();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // isSubscribed
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("isSubscribed")
  class IsSubscribed {

    @Test
    @DisplayName("returns true when user is subscribed")
    void isSubscribed_returnsTrue_whenUserIsSubscribed() {
      BotUser user = BotUser.builder().telegramUserId(1L).chatId(10L).subscribed(true).build();
      when(userRepository.findById(1L)).thenReturn(Optional.of(user));

      assertThat(service.isSubscribed(1L)).isTrue();
    }

    @Test
    @DisplayName("returns false when user is not subscribed")
    void isSubscribed_returnsFalse_whenUserIsNotSubscribed() {
      BotUser user = BotUser.builder().telegramUserId(1L).chatId(10L).subscribed(false).build();
      when(userRepository.findById(1L)).thenReturn(Optional.of(user));

      assertThat(service.isSubscribed(1L)).isFalse();
    }

    @Test
    @DisplayName("returns false when user does not exist")
    void isSubscribed_returnsFalse_whenUserDoesNotExist() {
      when(userRepository.findById(99L)).thenReturn(Optional.empty());

      assertThat(service.isSubscribed(99L)).isFalse();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // getUserLocale
  // ─────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("getUserLocale")
  class GetUserLocale {

    @Test
    @DisplayName("returns English locale when user has language code 'en'")
    void getUserLocale_returnsEnglish_whenLanguageCodeIsEn() {
      BotUser user = BotUser.builder().telegramUserId(1L).chatId(10L).languageCode("en").build();
      when(userRepository.findById(1L)).thenReturn(Optional.of(user));

      Locale result = service.getUserLocale(1L);

      assertThat(result).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("returns Ukrainian locale when user has language code 'uk'")
    void getUserLocale_returnsUkrainian_whenLanguageCodeIsUk() {
      BotUser user = BotUser.builder().telegramUserId(2L).chatId(20L).languageCode("uk").build();
      when(userRepository.findById(2L)).thenReturn(Optional.of(user));

      Locale result = service.getUserLocale(2L);

      assertThat(result.getLanguage()).isEqualTo("uk");
    }

    @Test
    @DisplayName("returns English locale when user is not found")
    void getUserLocale_returnsEnglish_whenUserNotFound() {
      when(userRepository.findById(99L)).thenReturn(Optional.empty());

      Locale result = service.getUserLocale(99L);

      assertThat(result).isEqualTo(Locale.ENGLISH);
    }
  }
}
