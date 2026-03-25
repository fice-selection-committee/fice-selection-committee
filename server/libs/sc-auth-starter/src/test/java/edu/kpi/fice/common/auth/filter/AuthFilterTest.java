package edu.kpi.fice.common.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import edu.kpi.fice.common.auth.client.IdentityServiceClient;
import edu.kpi.fice.common.auth.config.AuthProperties;
import edu.kpi.fice.common.auth.dto.PermissionDto;
import edu.kpi.fice.common.auth.dto.RoleDto;
import edu.kpi.fice.common.auth.dto.UserDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

  @Mock private IdentityServiceClient identityServiceClient;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain filterChain;

  private AuthFilter authFilter;
  private AuthProperties authProperties;

  @BeforeEach
  void setUp() {
    authProperties = new AuthProperties();
    authProperties.setSkipFilterPaths(List.of("/api/v1/webhooks"));
    authFilter = new AuthFilter(identityServiceClient, authProperties);
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldSkipFilterForConfiguredPaths() throws Exception {
    when(request.getRequestURI()).thenReturn("/api/v1/webhooks/test");

    authFilter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verifyNoInteractions(identityServiceClient);
  }

  @Test
  void shouldClearContextWhenNoAuthHeader() throws Exception {
    when(request.getRequestURI()).thenReturn("/api/v1/something");
    when(request.getHeader("Authorization")).thenReturn(null);

    authFilter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void shouldClearContextWhenInvalidAuthHeader() throws Exception {
    when(request.getRequestURI()).thenReturn("/api/v1/something");
    when(request.getHeader("Authorization")).thenReturn("Basic abc");

    authFilter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void shouldAuthenticateUserWithValidToken() throws Exception {
    when(request.getRequestURI()).thenReturn("/api/v1/something");
    when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");

    UserDto user =
        new UserDto(
            1L,
            "test@test.com",
            new RoleDto(1L, "ADMIN"),
            "John",
            "M",
            "Doe",
            Set.of(PermissionDto.builder().id(1L).name("READ").build()));
    when(identityServiceClient.getCurrentUser()).thenReturn(user);

    authFilter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .isEqualTo(user);
  }

  @Test
  void shouldReturnUnauthorizedWhenAuthFails() throws Exception {
    when(request.getRequestURI()).thenReturn("/api/v1/something");
    when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
    when(identityServiceClient.getCurrentUser()).thenThrow(new RuntimeException("Token invalid"));

    StringWriter sw = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(sw));

    authFilter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(filterChain, never()).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void shouldSkipWhenAlreadyAuthenticated() throws Exception {
    // Set up an existing authentication
    UserDto user =
        new UserDto(1L, "test@test.com", new RoleDto(1L, "ADMIN"), "John", "M", "Doe", Set.of());
    var auth =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            user, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);

    authFilter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verifyNoInteractions(identityServiceClient);
  }
}
