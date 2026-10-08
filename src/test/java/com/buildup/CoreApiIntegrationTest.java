package com.buildup;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class CoreApiIntegrationTest {

    private static final String PASSWORD = "BuildUp!234";

    @Autowired
    private WebApplicationContext applicationContext;

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

        MvcResult siteResult = mockMvc.perform(post("/api/sites")
                        .header("Authorization", bearer(ownerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "자동 테스트 현장",
                                  "address": "서울특별시 테스트로 1",
                                  "companyId": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.joinCode").isNotEmpty())
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

        MvcResult reportResult = mockMvc.perform(post("/api/sites/{siteId}/reports", siteId)
                        .header("Authorization", bearer(workerAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "FOUNDATION",
                                  "toProgress": 25.00,
                                  "memo": "기초 공정 작업 완료",
                                  "weather": "맑음",
                                  "workers": 4,
                                  "equipment": "굴착기 1대",
                                  "photoFileNames": [],
                                  "files": []
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.fromProgress").value(0))
                .andExpect(jsonPath("$.toProgress").value(25.0))
                .andReturn();

        long reportId = readNumber(reportResult, "$.id").longValue();

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

        mockMvc.perform(get("/api/sites/{siteId}/processes", siteId)
                        .header("Authorization", bearer(ownerAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].processKey").value("FOUNDATION"))
                .andExpect(jsonPath("$[0].progress").value(25.0));
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
}
