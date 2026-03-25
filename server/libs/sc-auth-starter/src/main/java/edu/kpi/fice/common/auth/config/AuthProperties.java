package edu.kpi.fice.common.auth.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("sc.auth")
public class AuthProperties {

  /** URL of the identity service used by the Feign client. */
  private String identityServiceUrl;

  /**
   * Request paths that should skip the auth filter entirely (e.g. webhook endpoints). Matched by
   * {@code URI.startsWith(...)}.
   */
  private List<String> skipFilterPaths = new ArrayList<>();

  /**
   * Paths that Spring Security will permitAll(). Swagger and actuator health are included by
   * default.
   */
  private List<String> publicPaths =
      new ArrayList<>(
          List.of("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health"));
}
