package edu.kpi.fice.telegram_service.service;

import edu.kpi.fice.telegram_service.domain.BotUser;
import edu.kpi.fice.telegram_service.repository.BotUserRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class BotUserService {

  private final BotUserRepository userRepository;

  public BotUserService(BotUserRepository userRepository, MeterRegistry meterRegistry) {
    this.userRepository = userRepository;
    Gauge.builder("bot.users.active", userRepository, BotUserRepository::count)
        .description("Total registered bot users")
        .register(meterRegistry);
  }

  @Transactional
  public BotUser resolveUser(
      Long telegramUserId,
      Long chatId,
      String telegramLanguageCode,
      Integer messageThreadId,
      String username) {
    return userRepository
        .findById(telegramUserId)
        .map(
            user -> {
              user.setChatId(chatId);
              user.setMessageThreadId(messageThreadId);
              if (username != null) {
                user.setUsername(username);
              }
              user.touch();
              return userRepository.save(user);
            })
        .orElseGet(
            () -> {
              String lang = resolveInitialLanguage(telegramLanguageCode);
              BotUser newUser =
                  BotUser.builder()
                      .telegramUserId(telegramUserId)
                      .chatId(chatId)
                      .languageCode(lang)
                      .messageThreadId(messageThreadId)
                      .username(username)
                      .build();
              log.info("New bot user registered: userId={}, language={}", telegramUserId, lang);
              return userRepository.save(newUser);
            });
  }

  @Transactional
  public BotUser updateLanguage(Long telegramUserId, String languageCode) {
    BotUser user =
        userRepository
            .findById(telegramUserId)
            .orElseThrow(() -> new IllegalStateException("User not found: " + telegramUserId));
    user.setLanguageCode(languageCode);
    return userRepository.save(user);
  }

  @Transactional
  public BotUser toggleSubscription(Long telegramUserId) {
    BotUser user =
        userRepository
            .findById(telegramUserId)
            .orElseThrow(() -> new IllegalStateException("User not found: " + telegramUserId));
    user.setSubscribed(!user.getSubscribed());
    return userRepository.save(user);
  }

  @Transactional(readOnly = true)
  public List<BotUser> getSubscribedUsers() {
    return userRepository.findAllBySubscribedTrue();
  }

  @Transactional(readOnly = true)
  public boolean isSubscribed(Long telegramUserId) {
    return userRepository.findById(telegramUserId).map(BotUser::getSubscribed).orElse(false);
  }

  @Transactional(readOnly = true)
  public java.util.Optional<BotUser> findUser(Long telegramUserId) {
    return userRepository.findById(telegramUserId);
  }

  @Transactional(readOnly = true)
  public String getDisplayName(Long telegramUserId) {
    return userRepository
        .findById(telegramUserId)
        .map(
            u ->
                u.getUsername() != null
                    ? "@" + u.getUsername()
                    : "telegram:" + telegramUserId)
        .orElse("telegram:" + telegramUserId);
  }

  @Transactional(readOnly = true)
  public Locale getUserLocale(Long telegramUserId) {
    return userRepository
        .findById(telegramUserId)
        .map(
            u ->
                edu.kpi.fice.telegram_service.i18n.BotMessageResolver.resolveLocale(
                    u.getLanguageCode()))
        .orElse(Locale.ENGLISH);
  }

  private String resolveInitialLanguage(String telegramLanguageCode) {
    if (telegramLanguageCode == null) {
      return "en";
    }
    return switch (telegramLanguageCode.toLowerCase()) {
      case "uk", "ua" -> "uk";
      default -> "en";
    };
  }
}
