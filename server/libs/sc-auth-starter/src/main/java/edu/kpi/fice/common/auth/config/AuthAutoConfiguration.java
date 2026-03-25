package edu.kpi.fice.common.auth.config;

import edu.kpi.fice.common.auth.client.FeignForwardHeadersInterceptor;
import edu.kpi.fice.common.auth.client.IdentityServiceClient;
import edu.kpi.fice.common.auth.filter.AuthFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@EnableConfigurationProperties(AuthProperties.class)
@EnableFeignClients(clients = IdentityServiceClient.class)
@Import(SecurityConfigDefaults.class)
public class AuthAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AuthFilter authFilter(
      IdentityServiceClient identityServiceClient, AuthProperties authProperties) {
    return new AuthFilter(identityServiceClient, authProperties);
  }

  @Bean
  @ConditionalOnMissingBean(FeignForwardHeadersInterceptor.class)
  public FeignForwardHeadersInterceptor feignForwardHeadersInterceptor() {
    return new FeignForwardHeadersInterceptor();
  }
}
