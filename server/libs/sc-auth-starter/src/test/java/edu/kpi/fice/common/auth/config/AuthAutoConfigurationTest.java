package edu.kpi.fice.common.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AuthAutoConfigurationTest {

  @Test
  void shouldHaveDefaultPublicPaths() {
    AuthProperties props = new AuthProperties();
    assertThat(props.getPublicPaths())
        .contains("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health");
  }

  @Test
  void shouldAllowCustomSkipFilterPaths() {
    AuthProperties props = new AuthProperties();
    props.setSkipFilterPaths(List.of("/api/v1/webhooks"));
    assertThat(props.getSkipFilterPaths()).containsExactly("/api/v1/webhooks");
  }

  @Test
  void shouldAllowSettingIdentityServiceUrl() {
    AuthProperties props = new AuthProperties();
    props.setIdentityServiceUrl("http://localhost:8081");
    assertThat(props.getIdentityServiceUrl()).isEqualTo("http://localhost:8081");
  }

  @Test
  void shouldAllowCustomPublicPaths() {
    AuthProperties props = new AuthProperties();
    props.setPublicPaths(List.of("/custom/**"));
    assertThat(props.getPublicPaths()).containsExactly("/custom/**");
  }
}
