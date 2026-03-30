package edu.kpi.fice.common.auth.client;

import edu.kpi.fice.common.auth.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "identity-service", url = "${sc.auth.identity-service-url}")
public interface IdentityServiceClient {

  @GetMapping("/api/v1/auth/user")
  UserDto getCurrentUser();
}
