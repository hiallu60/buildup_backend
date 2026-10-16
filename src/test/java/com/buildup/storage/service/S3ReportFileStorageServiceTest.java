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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3ReportFileStorageServiceTest {

    @Test
    void uploadsPdfUsingUuidKeyAndMetadata() {
        AtomicReference<PutObjectRequest> captured = new AtomicReference<>();
        S3ReportFileStorageService service = service(fakeS3Client(captured, new AtomicReference<>()), 1024);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "공정 확인서.pdf",
                "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)
        );

        var stored = service.upload(15L, file);

        assertThat(stored.s3Key()).matches("reports/15/files/[0-9a-f-]+\\.pdf");
        assertThat(stored.originalName()).isEqualTo("공정 확인서.pdf");
        assertThat(stored.contentType()).isEqualTo("application/pdf");
        assertThat(captured.get().bucket()).isEqualTo("buildup-test-bucket");
        assertThat(captured.get().key()).isEqualTo(stored.s3Key());
    }

    @Test
    void rejectsDisallowedExtensionBeforeS3Call() {
        S3ReportFileStorageService service = service(fakeS3Client(new AtomicReference<>(), new AtomicReference<>()), 1024);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.exe",
                "application/octet-stream",
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> service.upload(1L, file))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("REPORT_FILE_TYPE_NOT_ALLOWED");
    }

    @Test
    void rejectsContentThatDoesNotMatchExtension() {
        S3ReportFileStorageService service = service(fakeS3Client(new AtomicReference<>(), new AtomicReference<>()), 1024);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.pdf",
                "application/pdf",
                "not-a-pdf".getBytes(StandardCharsets.US_ASCII)
        );

        assertThatThrownBy(() -> service.upload(1L, file))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("REPORT_FILE_CONTENT_INVALID");
    }

    @Test
    void rejectsFileOverConfiguredLimit() {
        S3ReportFileStorageService service = service(fakeS3Client(new AtomicReference<>(), new AtomicReference<>()), 3);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.pdf",
                "application/pdf",
                "%PDF".getBytes(StandardCharsets.US_ASCII)
        );

        assertThatThrownBy(() -> service.upload(1L, file))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("REPORT_FILE_TOO_LARGE");
    }

    @Test
    void deletesStoredObjectByKey() {
        AtomicReference<DeleteObjectRequest> captured = new AtomicReference<>();
        S3ReportFileStorageService service = service(fakeS3Client(new AtomicReference<>(), captured), 1024);

        service.deleteQuietly(9L, "reports/9/files/file.pdf");

        assertThat(captured.get().bucket()).isEqualTo("buildup-test-bucket");
        assertThat(captured.get().key()).isEqualTo("reports/9/files/file.pdf");
    }

    @Test
    void acceptsDocxOnlyWhenZipContainsWordDocumentStructure() throws IOException {
        AtomicReference<PutObjectRequest> captured = new AtomicReference<>();
        S3ReportFileStorageService service = service(fakeS3Client(captured, new AtomicReference<>()), 4096);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "공정 확인서.docx",
                "application/zip",
                zipBytes("[Content_Types].xml", "word/document.xml")
        );

        var stored = service.upload(15L, file);

        assertThat(stored.s3Key()).matches("reports/15/files/[0-9a-f-]+\\.docx");
        assertThat(stored.contentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(captured.get()).isNotNull();
    }

    @Test
    void rejectsArbitraryZipRenamedAsDocx() throws IOException {
        S3ReportFileStorageService service = service(
                fakeS3Client(new AtomicReference<>(), new AtomicReference<>()),
                4096
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.docx",
                "application/zip",
                zipBytes("payload.txt")
        );

        assertThatThrownBy(() -> service.upload(1L, file))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("REPORT_FILE_CONTENT_INVALID");
    }

    @Test
    void rejectsKeyThatBelongsToAnotherReportBeforeDownload() {
        S3ReportFileStorageService service = service(
                fakeS3Client(new AtomicReference<>(), new AtomicReference<>()),
                1024
        );

        assertThatThrownBy(() -> service.download(
                9L,
                "reports/10/files/file.pdf",
                "application/pdf"
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("REPORT_FILE_CONTENT_NOT_FOUND");
    }

    @Test
    void skipsDeletionWhenKeyBelongsToAnotherReport() {
        AtomicReference<DeleteObjectRequest> captured = new AtomicReference<>();
        S3ReportFileStorageService service = service(fakeS3Client(new AtomicReference<>(), captured), 1024);

        service.deleteQuietly(9L, "reports/10/files/file.pdf");

        assertThat(captured.get()).isNull();
    }

    private S3ReportFileStorageService service(S3Client s3Client, long maxFileSizeBytes) {
        AwsProperties properties = new AwsProperties(
                "ap-northeast-2",
                new AwsProperties.S3("buildup-test-bucket", false, 1024, maxFileSizeBytes)
        );
        return new S3ReportFileStorageService(s3Client, properties);
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

    private byte[] zipBytes(String... entryNames) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String entryName : entryNames) {
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write("test".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
