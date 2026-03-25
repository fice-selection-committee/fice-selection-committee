package edu.kpi.fice.sc.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MinIOContainer;

@TestConfiguration(proxyBeanMethods = false)
public class MinioContainerConfig {
  @Bean
  MinIOContainer minioContainer() {
    return new MinIOContainer("minio/minio:latest");
  }

  @Bean
  DynamicPropertyRegistrar minioProperties(MinIOContainer minio) {
    return registry -> {
      registry.add("minio.endpoint-internal", minio::getS3URL);
      registry.add("minio.access-key", minio::getUserName);
      registry.add("minio.secret-key", minio::getPassword);
    };
  }
}
