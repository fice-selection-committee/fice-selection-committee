package edu.kpi.fice.sc.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@ConditionalOnProperty(name = "minio.bucket")
@RequiredArgsConstructor
public class S3BucketInitializer implements ApplicationRunner {
  private final S3Client s3Client;
  private final S3Properties props;

  @Override
  public void run(ApplicationArguments args) {
    // if bucket exists -> connect to it
    try {
      s3Client.headBucket(HeadBucketRequest.builder().bucket(props.bucket()).build());
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        s3Client.createBucket(CreateBucketRequest.builder().bucket(props.bucket()).build());
      } else throw e;
    }
  }
}
