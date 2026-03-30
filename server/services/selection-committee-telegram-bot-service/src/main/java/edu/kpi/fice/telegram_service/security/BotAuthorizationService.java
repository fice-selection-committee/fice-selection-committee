package edu.kpi.fice.telegram_service.security;

import edu.kpi.fice.telegram_service.repository.BotUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Role-based authorization checks for bot operations. */
@Service
@RequiredArgsConstructor
public class BotAuthorizationService {

  private static final String ROLE_OPERATOR = "operator";
  private static final String ROLE_ADMIN = "admin";

  private final BotUserRepository userRepository;

  public boolean canToggleFlags(Long userId) {
    return hasRole(userId, ROLE_OPERATOR, ROLE_ADMIN);
  }

  public boolean canViewAudit(Long userId) {
    return hasRole(userId, ROLE_OPERATOR, ROLE_ADMIN);
  }

  private boolean hasRole(Long userId, String... allowedRoles) {
    return userRepository
        .findById(userId)
        .map(
            user -> {
              for (String role : allowedRoles) {
                if (role.equalsIgnoreCase(user.getRole())) {
                  return true;
                }
              }
              return false;
            })
        .orElse(false);
  }
}
