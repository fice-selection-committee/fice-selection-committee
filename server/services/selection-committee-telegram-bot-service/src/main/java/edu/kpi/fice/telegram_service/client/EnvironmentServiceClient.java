package edu.kpi.fice.telegram_service.client;

import edu.kpi.fice.telegram_service.dto.FeatureFlagResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "environment-service", url = "${feature-flags.env-service-url}")
public interface EnvironmentServiceClient {

  @GetMapping("/api/v1/feature-flags")
  List<FeatureFlagResponse> getFlags(
      @RequestParam(required = false) String environment,
      @RequestParam(required = false) String scope);

  @GetMapping("/api/v1/feature-flags/{key}")
  FeatureFlagResponse getFlagByKey(@PathVariable String key);

  @PostMapping("/api/v1/feature-flags/{key}/toggle")
  FeatureFlagResponse toggleFlag(
      @PathVariable String key,
      @RequestParam boolean enabled,
      @RequestHeader("X-Actor-ID") String actorId);
}
