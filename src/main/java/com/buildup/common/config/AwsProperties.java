package com.buildup.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws")
public record AwsProperties(
        String region,
        S3 s3
) {

    public AwsProperties {
        if (region == null || region.isBlank()) {
            throw new IllegalStateException("AWS region must be configured");
        }
        if (s3 == null) {
            throw new IllegalStateException("AWS S3 settings must be configured");
        }
    }

    public record S3(
            String bucket,
            boolean connectionCheckEnabled
    ) {

        public S3 {
            if (bucket == null || bucket.isBlank()) {
                throw new IllegalStateException("AWS S3 bucket must be configured");
            }
        }
    }
}
