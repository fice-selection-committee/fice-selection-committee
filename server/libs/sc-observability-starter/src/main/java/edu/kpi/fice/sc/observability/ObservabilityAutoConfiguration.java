package edu.kpi.fice.sc.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {

  @Bean
  MeterRegistryCustomizer<MeterRegistry> commonTags(ObservabilityProperties properties) {
    return registry ->
        registry
            .config()
            .commonTags(
                "application", properties.getApplicationName(),
                "environment", properties.getEnvironment());
  }
}
