package edu.kpi.fice.common.auth.util;

import edu.kpi.fice.common.auth.dto.UserDto;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@UtilityClass
public class AuthUtils {
  public static UserDto getUserFromContext() {
    SecurityContext securityContext = SecurityContextHolder.getContext();
    Authentication authentication = securityContext.getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new SecurityException("User is not authenticated");
    }
    Object principal = authentication.getPrincipal();
    if (principal instanceof UserDto user) {
      return user;
    } else {
      throw new SecurityException("Context does not contain valid UserDto");
    }
  }
}
