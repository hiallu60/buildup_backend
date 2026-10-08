package com.buildup.storage.service;

import com.buildup.common.config.AwsProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class S3ConnectionServiceTest {

    @Test
    void checksConfiguredBucketWithoutUploadingAnObject() {
        AtomicReference<HeadBucketRequest> capturedRequest = new AtomicReference<>();
        S3Client s3Client = fakeS3Client(capturedRequest);
        AwsProperties properties = new AwsProperties(
                "ap-northeast-2",
                new AwsProperties.S3("buildup-test-bucket", false)
        );
        S3ConnectionService service = new S3ConnectionService(s3Client, properties);

        service.checkConnection();

        assertThat(capturedRequest.get()).isNotNull();
        assertThat(capturedRequest.get().bucket()).isEqualTo("buildup-test-bucket");
    }

    private S3Client fakeS3Client(AtomicReference<HeadBucketRequest> capturedRequest) {
        return (S3Client) Proxy.newProxyInstance(
                S3Client.class.getClassLoader(),
                new Class<?>[]{S3Client.class},
                (proxy, method, arguments) -> {
                    if ("headBucket".equals(method.getName())
                            && arguments != null
                            && arguments.length == 1
                            && arguments[0] instanceof HeadBucketRequest request) {
                        capturedRequest.set(request);
                        return HeadBucketResponse.builder().build();
                    }
                    if ("serviceName".equals(method.getName())) {
                        return "s3";
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
