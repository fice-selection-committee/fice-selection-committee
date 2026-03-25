package edu.kpi.fice.sc.s3;

import java.net.URI;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@AutoConfiguration
@EnableConfigurationProperties(S3Properties.class)
@ConditionalOnClass(S3Client.class)
public class S3AutoConfiguration {

  @Bean
  public S3Client s3Client(S3Properties props) {
    var credentialsProvider =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
    // init S3 client with region & credentials
    var builder =
        S3Client.builder()
            .region(Region.of(props.region()))
            .credentialsProvider(credentialsProvider);
    // if custom endpoint provided (e.g. MinIO instead of AWS)
    if (props.endpointInternal() != null && !props.endpointInternal().isBlank()) {
      builder =
          builder
              .endpointOverride(URI.create(props.endpointInternal()))
              .serviceConfiguration(
                  S3Configuration.builder().pathStyleAccessEnabled(props.pathStyle()).build());
    }
    return builder.build();
  }

  @Bean
  public S3Presigner s3Presigner(S3Properties props) {
    var credentialsProvider =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
    var builder =
        S3Presigner.builder()
            .region(Region.of(props.region()))
            .credentialsProvider(credentialsProvider);
    // if custom endpoint provided (e.g. MinIO instead of AWS)
    if (props.endpointExternal() != null && !props.endpointExternal().isBlank()) {
      builder =
          builder
              .endpointOverride(URI.create(props.endpointExternal()))
              .serviceConfiguration(
                  S3Configuration.builder().pathStyleAccessEnabled(props.pathStyle()).build());
    }
    return builder.build();
  }
}
