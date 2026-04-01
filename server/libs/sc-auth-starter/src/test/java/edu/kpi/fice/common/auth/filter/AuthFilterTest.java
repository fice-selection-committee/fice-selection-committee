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
  void shouldClearContextWhenNoAuthHeader() throws Exception {
    when(request.getHeader("Authorization")).thenReturn(null);

    authFilter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void shouldClearContextWhenInvalidAuthHeader() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Basic abc");

    authFilter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void shouldAuthenticateUserWithValidToken() throws Exception {
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
  void shouldClearContextAndContinueFilterChainWhenAuthFails() throws Exception {
    when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
    when(identityServiceClient.getCurrentUser()).thenThrow(new RuntimeException("Token invalid"));

    authFilter.doFilterInternal(request, response, filterChain);

    // Filter should NOT short-circuit — Spring Security decides access
    verify(filterChain).doFilter(request, response);
    verify(response, never()).setStatus(anyInt());
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void shouldNotFilterForPublicPaths() throws Exception {
    authProperties.setPublicPaths(
        new java.util.ArrayList<>(
            java.util.List.of("/actuator/health", "/api/v1/notifications/stream")));
    // Recreate filter with updated properties
    authFilter = new AuthFilter(identityServiceClient, authProperties);

    when(request.getRequestURI()).thenReturn("/api/v1/notifications/stream");

    assertThat(authFilter.shouldNotFilter(request)).isTrue();
  }

  @Test
  void shouldNotFilterForPublicPathsWithWildcard() throws Exception {
    authProperties.setPublicPaths(
        new java.util.ArrayList<>(java.util.List.of("/v3/api-docs/**", "/swagger-ui/**")));
    authFilter = new AuthFilter(identityServiceClient, authProperties);

    when(request.getRequestURI()).thenReturn("/v3/api-docs/some-resource");

    assertThat(authFilter.shouldNotFilter(request)).isTrue();
  }

  @Test
  void shouldFilterForNonPublicPaths() throws Exception {
    authProperties.setPublicPaths(new java.util.ArrayList<>(java.util.List.of("/actuator/health")));
    authFilter = new AuthFilter(identityServiceClient, authProperties);

    when(request.getRequestURI()).thenReturn("/api/v1/users");

    assertThat(authFilter.shouldNotFilter(request)).isFalse();
  }

  @Test
  void shouldNotFilterForSkipFilterPaths() throws Exception {
    when(request.getRequestURI()).thenReturn("/api/v1/webhooks/test");

    assertThat(authFilter.shouldNotFilter(request)).isTrue();
  }

  @Test
  void shouldNotFilterReturnsFalseWhenBothPathListsAreEmpty() throws Exception {
    authProperties.setSkipFilterPaths(List.of());
    authProperties.setPublicPaths(new java.util.ArrayList<>());
    authFilter = new AuthFilter(identityServiceClient, authProperties);

    when(request.getRequestURI()).thenReturn("/api/v1/users");

    assertThat(authFilter.shouldNotFilter(request)).isFalse();
  }

  @Test
  void shouldNotSkipForPrefixCollisionOnSkipFilterPaths() throws Exception {
    // /api/v1/webhooks is a skip path, but /api/v1/webhooksmalicious should NOT match
    when(request.getRequestURI()).thenReturn("/api/v1/webhooksmalicious");

    assertThat(authFilter.shouldNotFilter(request)).isFalse();
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
