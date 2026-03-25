package edu.kpi.fice.sc.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
public record S3Properties(
    String endpointInternal,
    String endpointExternal,
    String region,
    String bucket,
    String accessKey,
    String secretKey,
    boolean pathStyle, // Enables path-style access (http://host:port/bucket/key)
    String sse, // encryption mode
    String kmsKeyId,
    String layoutPrefix // root folder prefix
    ) {}
