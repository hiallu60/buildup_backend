package com.buildup;

import com.buildup.site.entity.MembershipStatus;
import com.buildup.site.entity.Site;
import com.buildup.site.entity.SiteMember;
import com.buildup.site.entity.SiteMemberRole;
import com.buildup.site.repository.SiteMemberRepository;
import com.buildup.site.repository.SiteRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Import(CoreApiIntegrationTest.S3TestConfiguration.class)
class CoreApiIntegrationTest {

    private static final String PASSWORD = "BuildUp!234";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private SiteMemberRepository siteMemberRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void authenticationFlowCoversLoginJwtRefreshAndLogoutRevocation() throws Exception {
        signup("인증 사용자", "auth-flow@buildup.test");

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "auth-flow@buildup.test",
                                  "password": "BuildUp!234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        String accessToken = readString(loginResult, "$.accessToken");
        String refreshToken = readString(loginResult, "$.refreshToken");

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("auth-flow@buildup.test"));

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").value(refreshToken))
                .andReturn();

        String refreshedAccessToken = readString(refreshResult, "$.accessToken");
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", bearer(refreshedAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("auth-flow@buildup.test"));

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void siteAndReportFlowCoversJoinDuplicatePermissionCreateAndApprove() throws Exception {
        String ownerAccessToken = signup("현장 소유자", "owner-flow@buildup.test").accessToken();
        String workerAccessToken = signup("현장 작업자", "worker-flow@buildup.test").accessToken();
        String outsiderAccessToken = signup("외부 사용자", "outsider-flow@buildup.test").accessToken();

        MvcResult siteResult = mockMvc.perform(post("/api/sites")
                        .header("Authorization", bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "자동 테스트 현장",
                                  "address": "서울특별시 테스트로 1",
                                  "workType": "건축",
                                  "latitude": 37.5665000,
                                  "longitude": 126.9780000,
                                  "progressCalculationMode": "PROCESS_WEIGHTED",
                                  "companyId": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.joinCode").isNotEmpty())
                .andExpect(jsonPath("$.workType").value("건축"))
                .andExpect(jsonPath("$.latitude").value(37.5665))
                .andExpect(jsonPath("$.progressCalculationMode").value("PROCESS_WEIGHTED"))
                .andReturn();

        long siteId = readNumber(siteResult, "$.id").longValue();
        String joinCode = readString(siteResult, "$.joinCode");

        mockMvc.perform(post("/api/sites/join")
                        .header("Authorization", bearer(workerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s"}
                                """.formatted(joinCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(siteId))
                .andExpect(jsonPath("$.myRole").value("WORKER"));

        mockMvc.perform(post("/api/sites/join")
                        .header("Authorization", bearer(workerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s"}
                                """.formatted(joinCode)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SITE_MEMBER"));

        mockMvc.perform(post("/api/sites/{siteId}/processes", siteId)
                        .header("Authorization", bearer(workerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(processBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SITE_MANAGEMENT_FORBIDDEN"));

        mockMvc.perform(post("/api/sites/{siteId}/processes", siteId)
                        .header("Authorization", bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(processBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.processKey").value("FOUNDATION"))
                .andExpect(jsonPath("$.progress").value(0));

        mockMvc.perform(post("/api/sites/{siteId}/reports", siteId)
                        .header("Authorization", bearer(workerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": null,
                                  "toProgress": 10.00,
                                  "work": "공정 없는 보고 시도",
                                  "photoFileNames": [],
                                  "files": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT_PROCESS_REQUIRED"));

        MvcResult reportResult = mockMvc.perform(post("/api/sites/{siteId}/reports", siteId)
                        .header("Authorization", bearer(workerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "FOUNDATION",
                                  "toProgress": 25.00,
                                  "work": "기초 철근 배근",
                                  "memo": "기초 공정 작업 완료",
                                  "weather": "맑음",
                                  "workers": 4,
                                  "equipment": "굴착기 1대",
                                  "submitLatitude": 37.5665000,
                                  "submitLongitude": 126.9780000,
                                  "submitAccuracy": 8.50,
                                  "photoFileNames": [],
                                  "files": []
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.fromProgress").value(0))
                .andExpect(jsonPath("$.toProgress").value(25.0))
                .andExpect(jsonPath("$.work").value("기초 철근 배근"))
                .andExpect(jsonPath("$.submitAccuracy").value(8.5))
                .andExpect(jsonPath("$.checks", hasSize(0)))
                .andReturn();

        long reportId = readNumber(reportResult, "$.id").longValue();

        mockMvc.perform(get("/api/reports/{reportId}/files", reportId)
                        .header("Authorization", bearer(outsiderAccessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));

        mockMvc.perform(get("/api/reports/{reportId}/photos", reportId)
                        .header("Authorization", bearer(outsiderAccessToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));

        MockMultipartFile unauthorizedPhoto = new MockMultipartFile(
                "file",
                "권한 없음.jpg",
                "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01}
        );
        mockMvc.perform(multipart("/api/reports/{reportId}/photos", reportId)
                        .file(unauthorizedPhoto)
                        .header("Authorization", bearer(outsiderAccessToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REPORT_PHOTO_EDIT_FORBIDDEN"));

        MockMultipartFile unauthorizedAttachment = new MockMultipartFile(
                "file",
                "권한 없음.pdf",
                "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)
        );
        mockMvc.perform(multipart("/api/reports/{reportId}/files", reportId)
                        .file(unauthorizedAttachment)
                        .header("Authorization", bearer(outsiderAccessToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REPORT_FILE_EDIT_FORBIDDEN"));

        MockMultipartFile attachment = new MockMultipartFile(
                "file",
                "공정 확인서.pdf",
                "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)
        );
        mockMvc.perform(multipart("/api/reports/{reportId}/files", reportId)
                        .file(attachment)
                        .header("Authorization", bearer(workerAccessToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalName").value("공정 확인서.pdf"))
                .andExpect(jsonPath("$.contentUrl").isNotEmpty());

        mockMvc.perform(get("/api/reports/{reportId}/files", reportId)
                        .header("Authorization", bearer(ownerAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].contentType").value("application/pdf"));

        mockMvc.perform(post("/api/reports/{reportId}/approve", reportId)
                        .header("Authorization", bearer(workerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment": "작업자 승인 시도"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REPORT_REVIEW_FORBIDDEN"));

        mockMvc.perform(post("/api/reports/{reportId}/approve", reportId)
                        .header("Authorization", bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment": "승인합니다"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(reportId))
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"));

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", bearer(workerAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("REPORT_APPROVED"))
                .andExpect(jsonPath("$[0].referenceType").value("REPORT"))
                .andExpect(jsonPath("$[0].referenceId").value(reportId));

        mockMvc.perform(get("/api/sites/{siteId}/processes", siteId)
                        .header("Authorization", bearer(ownerAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].processKey").value("FOUNDATION"))
                .andExpect(jsonPath("$[0].progress").value(25.0));

        mockMvc.perform(get("/api/sites/{siteId}", siteId)
                        .header("Authorization", bearer(ownerAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressCalculationMode").value("PROCESS_WEIGHTED"))
                .andExpect(jsonPath("$.overallProgress").value(25.0));
    }

    @Test
    void freeFormReportUsesWorkWithoutLegacyProcess() throws Exception {
        String ownerAccessToken = signup(
                "자유 보고 현장 소유자",
                "free-report-owner@buildup.test"
        ).accessToken();
        MvcResult siteResult = mockMvc.perform(post("/api/sites")
                        .header("Authorization", bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "자유 보고 현장",
                                  "address": "서울특별시 자유로 1",
                                  "workType": "욕실 리모델링",
                                  "progressCalculationMode": "MANUAL_INPUT",
                                  "companyId": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.progressCalculationMode").value("MANUAL_INPUT"))
                .andReturn();
        long siteId = readNumber(siteResult, "$.id").longValue();

        mockMvc.perform(post("/api/sites/{siteId}/reports", siteId)
                        .header("Authorization", bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "FOUNDATION",
                                  "work": "직접 입력 현장의 공정 보고 시도",
                                  "toProgress": 5.00,
                                  "photoFileNames": [],
                                  "files": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT_PROCESS_NOT_ALLOWED"));

        MvcResult reportResult = mockMvc.perform(post("/api/sites/{siteId}/reports", siteId)
                        .header("Authorization", bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": null,
                                  "work": "욕실 타일 철거 및 방수 작업",
                                  "toProgress": 15.00,
                                  "photoFileNames": [],
                                  "files": []
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.processKey").doesNotExist())
                .andExpect(jsonPath("$.processName").doesNotExist())
                .andExpect(jsonPath("$.work").value("욕실 타일 철거 및 방수 작업"))
                .andExpect(jsonPath("$.fromProgress").value(0))
                .andReturn();
        long reportId = readNumber(reportResult, "$.id").longValue();

        mockMvc.perform(post("/api/reports/{reportId}/approve", reportId)
                        .header("Authorization", bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"현장 전체 진행률 승인"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"));

        mockMvc.perform(get("/api/sites/{siteId}", siteId)
                        .header("Authorization", bearer(ownerAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressCalculationMode").value("MANUAL_INPUT"))
                .andExpect(jsonPath("$.overallProgress").value(15.0));

        mockMvc.perform(post("/api/sites/{siteId}/processes", siteId)
                        .header("Authorization", bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(processBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SITE_PROCESS_NOT_ALLOWED"));
    }

    @Test
    void siteOwnerAndOwnerMembershipMustRemainConsistent() throws Exception {
        String ownerAccessToken = signup("소유자 일관성", "owner-consistency@buildup.test").accessToken();

        MvcResult siteResult = mockMvc.perform(post("/api/sites")
                        .header("Authorization", bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "소유자 검증 현장",
                                  "address": "서울특별시 검증로 1",
                                  "workType": "건축",
                                  "latitude": null,
                                  "longitude": null,
                                  "companyId": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        long siteId = readNumber(siteResult, "$.id").longValue();
        String joinCode = readString(siteResult, "$.joinCode");
        Site site = siteRepository.findByCode(joinCode).orElseThrow();
        var ownerMemberships = siteMemberRepository.findAllBySiteIdAndMemberRole(
                siteId,
                SiteMemberRole.OWNER
        );

        assertThat(ownerMemberships).hasSize(1);
        SiteMember ownerMembership = ownerMemberships.get(0);
        assertThat(ownerMembership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(ownerMembership.getUser().getId()).isEqualTo(site.getOwner().getId());

        ownerMembership.changeRole(SiteMemberRole.WORKER);
        siteMemberRepository.saveAndFlush(ownerMembership);

        mockMvc.perform(put("/api/sites/{siteId}", siteId)
                        .header("Authorization", bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "변경 시도 현장",
                                  "address": "서울특별시 검증로 2",
                                  "workType": "건축",
                                  "latitude": null,
                                  "longitude": null
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SITE_OWNER_MEMBERSHIP_INCONSISTENT"));
    }

    private TokenPair signup(String name, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "email": "%s",
                                  "phone": "010-1234-5678",
                                  "password": "%s"
                                }
                                """.formatted(name, email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        return new TokenPair(
                readString(result, "$.accessToken"),
                readString(result, "$.refreshToken")
        );
    }

    private String refreshBody(String refreshToken) {
        return """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);
    }

    private String processBody() {
        return """
                {
                  "processKey": "FOUNDATION",
                  "name": "기초 공사",
                  "weight": 100.00,
                  "planStart": "2026-10-01",
                  "planEnd": "2026-10-31",
                  "sortOrder": 1
                }
                """;
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String readString(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private Number readNumber(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private record TokenPair(String accessToken, String refreshToken) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class S3TestConfiguration {

        @Bean
        @Primary
        S3Client testS3Client() {
            return (S3Client) Proxy.newProxyInstance(
                    S3Client.class.getClassLoader(),
                    new Class<?>[]{S3Client.class},
                    (proxy, method, arguments) -> {
                        if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(method.getName())) {
                            return proxy == arguments[0];
                        }
                        if ("toString".equals(method.getName())) {
                            return "TestS3Client";
                        }
                        if ("putObject".equals(method.getName())
                                && arguments != null
                                && arguments.length == 2
                                && arguments[0] instanceof PutObjectRequest
                                && arguments[1] instanceof RequestBody) {
                            return PutObjectResponse.builder().build();
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
}
