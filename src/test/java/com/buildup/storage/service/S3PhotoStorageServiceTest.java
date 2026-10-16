package com.buildup.storage.service;

import com.buildup.common.config.AwsProperties;
import com.buildup.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3PhotoStorageServiceTest {

    @Test
    void uploadsJpegUsingUuidKeyAndMetadata() {
        AtomicReference<PutObjectRequest> captured = new AtomicReference<>();
        S3PhotoStorageService service = service(fakeS3Client(captured, new AtomicReference<>()), 1024);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "현장 사진.jpg",
                "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01}
        );

        var stored = service.upload(15L, file);

        assertThat(stored.s3Key()).matches("reports/15/photos/[0-9a-f-]+\\.jpg");
        assertThat(stored.originalName()).isEqualTo("현장 사진.jpg");
        assertThat(stored.contentType()).isEqualTo("image/jpeg");
        assertThat(stored.sizeBytes()).isEqualTo(4);
        assertThat(captured.get().bucket()).isEqualTo("buildup-test-bucket");
        assertThat(captured.get().key()).isEqualTo(stored.s3Key());
    }

    @Test
    void rejectsDisallowedContentTypeBeforeS3Call() {
        S3PhotoStorageService service = service(fakeS3Client(new AtomicReference<>(), new AtomicReference<>()), 1024);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.svg",
                "image/svg+xml",
                "<svg/>".getBytes()
        );

        assertThatThrownBy(() -> service.upload(1L, file))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("PHOTO_TYPE_NOT_ALLOWED");
    }

    @Test
    void rejectsContentThatDoesNotMatchDeclaredImageType() {
        S3PhotoStorageService service = service(fakeS3Client(new AtomicReference<>(), new AtomicReference<>()), 1024);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.png",
                "image/png",
                "not-a-png".getBytes()
        );

        assertThatThrownBy(() -> service.upload(1L, file))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("PHOTO_CONTENT_INVALID");
    }

    @Test
    void rejectsPhotoOverConfiguredLimit() {
        S3PhotoStorageService service = service(fakeS3Client(new AtomicReference<>(), new AtomicReference<>()), 3);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.jpg",
                "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01}
        );

        assertThatThrownBy(() -> service.upload(1L, file))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("PHOTO_TOO_LARGE");
    }

    @Test
    void deletesStoredObjectByKey() {
        AtomicReference<DeleteObjectRequest> captured = new AtomicReference<>();
        S3PhotoStorageService service = service(fakeS3Client(new AtomicReference<>(), captured), 1024);

        service.deleteQuietly(9L, "reports/9/photos/photo.jpg");

        assertThat(captured.get().bucket()).isEqualTo("buildup-test-bucket");
        assertThat(captured.get().key()).isEqualTo("reports/9/photos/photo.jpg");
    }

    @Test
    void rejectsKeyThatBelongsToAnotherReportBeforeDownload() {
        S3PhotoStorageService service = service(
                fakeS3Client(new AtomicReference<>(), new AtomicReference<>()),
                1024
        );

        assertThatThrownBy(() -> service.download(
                9L,
                "reports/10/photos/photo.jpg",
                "image/jpeg"
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("REPORT_PHOTO_CONTENT_NOT_FOUND");
    }

    @Test
    void skipsDeletionWhenKeyBelongsToAnotherReport() {
        AtomicReference<DeleteObjectRequest> captured = new AtomicReference<>();
        S3PhotoStorageService service = service(fakeS3Client(new AtomicReference<>(), captured), 1024);

        service.deleteQuietly(9L, "reports/10/photos/photo.jpg");

        assertThat(captured.get()).isNull();
    }

    @Test
    void removesClientPathAndControlCharactersFromOriginalName() {
        S3PhotoStorageService service = service(
                fakeS3Client(new AtomicReference<>(), new AtomicReference<>()),
                1024
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "C:\\fakepath\\현장\r사진.jpg",
                "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01}
        );

        var stored = service.upload(15L, file);

        assertThat(stored.originalName()).isEqualTo("현장_사진.jpg");
    }

    private S3PhotoStorageService service(S3Client s3Client, long maxPhotoSizeBytes) {
        AwsProperties properties = new AwsProperties(
                "ap-northeast-2",
                new AwsProperties.S3("buildup-test-bucket", false, maxPhotoSizeBytes, 20 * 1024 * 1024)
        );
        return new S3PhotoStorageService(s3Client, properties);
    }

    private S3Client fakeS3Client(
            AtomicReference<PutObjectRequest> putRequest,
            AtomicReference<DeleteObjectRequest> deleteRequest
    ) {
        return (S3Client) Proxy.newProxyInstance(
                S3Client.class.getClassLoader(),
                new Class<?>[]{S3Client.class},
                (proxy, method, arguments) -> {
                    if ("putObject".equals(method.getName())
                            && arguments != null
                            && arguments.length == 2
                            && arguments[0] instanceof PutObjectRequest request
                            && arguments[1] instanceof RequestBody) {
                        putRequest.set(request);
                        return PutObjectResponse.builder().build();
                    }
                    if ("deleteObject".equals(method.getName())
                            && arguments != null
                            && arguments.length == 1
                            && arguments[0] instanceof DeleteObjectRequest request) {
                        deleteRequest.set(request);
                        return DeleteObjectResponse.builder().build();
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
