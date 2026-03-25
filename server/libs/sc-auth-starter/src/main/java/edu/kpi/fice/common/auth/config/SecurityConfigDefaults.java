package edu.kpi.fice.common.auth.config;

import edu.kpi.fice.common.auth.filter.AuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfigDefaults {
  private final AuthFilter authFilter;
  private final AuthProperties authProperties;

  @Bean
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    String[] publicPaths = authProperties.getPublicPaths().toArray(String[]::new);

    http.cors(AbstractHttpConfigurer::disable)
        .csrf(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                    (request, response, authException) ->
                        response.sendError(
                            HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")))
        .authorizeHttpRequests(
            request ->
                request.requestMatchers(publicPaths).permitAll().anyRequest().authenticated())
        .sessionManagement(
            management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
