package edu.kpi.fice.common.auth.filter;

import edu.kpi.fice.common.auth.client.IdentityServiceClient;
import edu.kpi.fice.common.auth.config.AuthProperties;
import edu.kpi.fice.common.auth.dto.PermissionDto;
import edu.kpi.fice.common.auth.dto.UserDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {
  private final IdentityServiceClient identityServiceClient;
  private final AuthProperties authProperties;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // If already authenticated (e.g. by a preceding filter like WebhookAuthFilter), skip
    Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
    if (existingAuth != null && existingAuth.isAuthenticated()) {
      filterChain.doFilter(request, response);
      return;
    }

    // Skip filter for configured paths (e.g. webhook endpoints)
    String uri = request.getRequestURI();
    for (String skipPath : authProperties.getSkipFilterPaths()) {
      if (uri.startsWith(skipPath)) {
        filterChain.doFilter(request, response);
        return;
      }
    }

    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      SecurityContextHolder.clearContext();
      filterChain.doFilter(request, response);
      return;
    }
    try {
      UserDto authUser = identityServiceClient.getCurrentUser();
      ArrayList<String> permissions =
          new ArrayList<>(authUser.extraPermissions().stream().map(PermissionDto::name).toList());
      String roleName = authUser.role().name();
      permissions.add(roleName);
      // Also add the short form (without ROLE_ prefix) so @PreAuthorize("hasAuthority('ADMIN')")
      // works
      if (roleName.startsWith("ROLE_")) {
        permissions.add(roleName.substring(5));
      }
      Authentication authentication =
          new UsernamePasswordAuthenticationToken(
              authUser, null, permissions.stream().map(SimpleGrantedAuthority::new).toList());
      SecurityContext context = SecurityContextHolder.getContext();
      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);
    } catch (Exception e) {
      log.warn("Bearer token validation failed ({}): {} — "
              + "clearing security context and delegating to Spring Security",
          request.getRequestURI(), e.getMessage());
      SecurityContextHolder.clearContext();
    }
    filterChain.doFilter(request, response);
  }
}
