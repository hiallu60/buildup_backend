package com.buildup.material;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class MaterialRequestIntegrationTest {

    private static final String PASSWORD = "BuildUp!234";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void createRejectsInvalidItemsProcessAndPastNeededDate() throws Exception {
        SiteFixture site = createSiteWithProcess(
                "material-validation-owner@buildup.test",
                "자재 검증 현장"
        );

        mockMvc.perform(post("/api/sites/{siteId}/material-requests", site.siteId())
                        .header("Authorization", bearer(site.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialBody("FOUNDATION", LocalDate.now().plusDays(1), "[]")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/sites/{siteId}/material-requests", site.siteId())
                        .header("Authorization", bearer(site.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialBody(
                                "FOUNDATION",
                                LocalDate.now().plusDays(1),
                                "[{\"name\":\"시멘트\",\"quantity\":0,\"unit\":\"포\"}]"
                        )))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/sites/{siteId}/material-requests", site.siteId())
                        .header("Authorization", bearer(site.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialBody(
                                "UNKNOWN",
                                LocalDate.now().plusDays(1),
                                validItems()
                        )))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SITE_PROCESS_NOT_FOUND"));

        mockMvc.perform(post("/api/sites/{siteId}/material-requests", site.siteId())
                        .header("Authorization", bearer(site.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialBody(
                                "FOUNDATION",
                                LocalDate.now().minusDays(1),
                                validItems()
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MATERIAL_NEEDED_BY_IN_PAST"));
    }

    @Test
    void createsSiteWideRequestWithoutProcess() throws Exception {
        SiteFixture site = createSiteWithProcess(
                "material-site-wide-owner@buildup.test",
                "현장 공통 자재 요청 현장"
        );

        mockMvc.perform(post("/api/sites/{siteId}/material-requests", site.siteId())
                        .header("Authorization", bearer(site.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": null,
                                  "neededBy": null,
                                  "urgent": true,
                                  "note": "현장 공통 안전 장비",
                                  "items": [{"name":"안전모","quantity":5,"unit":"개"}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.processKey").doesNotExist())
                .andExpect(jsonPath("$.processName").doesNotExist())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void statusFlowEnforcesPermissionTransitionReasonAndEventHistory() throws Exception {
        SiteFixture site = createSiteWithProcess(
                "material-flow-owner@buildup.test",
                "자재 흐름 현장"
        );
        String workerToken = signup("자재 작업자", "material-flow-worker@buildup.test");
        joinSite(workerToken, site.joinCode());

        long requestId = createMaterialRequest(workerToken, site.siteId());

        mockMvc.perform(patch("/api/material-requests/{requestId}/status", requestId)
                        .header("Authorization", bearer(workerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("APPROVED", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MATERIAL_REQUEST_MANAGEMENT_FORBIDDEN"));

        mockMvc.perform(patch("/api/material-requests/{requestId}/status", requestId)
                        .header("Authorization", bearer(site.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("APPROVED", "승인 의견은 반려 사유 칸에 입력할 수 없음")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MATERIAL_REJECT_REASON_NOT_ALLOWED"));

        mockMvc.perform(patch("/api/material-requests/{requestId}/status", requestId)
                        .header("Authorization", bearer(site.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("APPROVED", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.events", hasSize(2)))
                .andExpect(jsonPath("$.events[1].status").value("APPROVED"));

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", bearer(workerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("MATERIAL_REQUEST_APPROVED"))
                .andExpect(jsonPath("$[0].referenceType").value("MATERIAL_REQUEST"))
                .andExpect(jsonPath("$[0].referenceId").value(requestId));

        mockMvc.perform(patch("/api/material-requests/{requestId}/status", requestId)
                        .header("Authorization", bearer(site.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("DELIVERED", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_MATERIAL_STATUS_TRANSITION"));

        changeStatus(site.ownerToken(), requestId, "ORDERED");
        changeStatus(site.ownerToken(), requestId, "DELIVERED");

        mockMvc.perform(get("/api/material-requests/{requestId}", requestId)
                        .header("Authorization", bearer(site.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.events", hasSize(4)))
                .andExpect(jsonPath("$.events[3].status").value("DELIVERED"));

        long rejectedRequestId = createMaterialRequest(workerToken, site.siteId());
        mockMvc.perform(patch("/api/material-requests/{requestId}/status", rejectedRequestId)
                        .header("Authorization", bearer(site.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("REJECTED", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MATERIAL_REJECT_REASON_REQUIRED"));

        mockMvc.perform(patch("/api/material-requests/{requestId}/status", rejectedRequestId)
                        .header("Authorization", bearer(site.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("REJECTED", "재고 확인 필요")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("재고 확인 필요"))
                .andExpect(jsonPath("$.events[1].status").value("REJECTED"));

        String outsiderToken = signup("외부 사용자", "material-outsider@buildup.test");
        mockMvc.perform(get("/api/material-requests/{requestId}", requestId)
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void inconsistentLatestEventBlocksFurtherTransition() throws Exception {
        SiteFixture site = createSiteWithProcess(
                "material-history-owner@buildup.test",
                "자재 이력 검증 현장"
        );
        long requestId = createMaterialRequest(site.ownerToken(), site.siteId());

        jdbcTemplate.update(
                "update material_requests set status = 'APPROVED' where id = ?",
                requestId
        );

        mockMvc.perform(patch("/api/material-requests/{requestId}/status", requestId)
                        .header("Authorization", bearer(site.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("ORDERED", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATERIAL_STATUS_HISTORY_INCONSISTENT"));
    }

    @Test
    void concurrentApprovalAllowsOnlyOneSuccessfulTransition() throws Exception {
        SiteFixture site = createSiteWithProcess(
                "material-concurrency-owner@buildup.test",
                "자재 동시 처리 현장"
        );
        long requestId = createMaterialRequest(site.ownerToken(), site.siteId());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            var task = (java.util.concurrent.Callable<Integer>) () -> {
                start.await();
                return mockMvc.perform(patch("/api/material-requests/{requestId}/status", requestId)
                                .header("Authorization", bearer(site.ownerToken()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(statusBody("APPROVED", null)))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            Future<Integer> first = executor.submit(task);
            Future<Integer> second = executor.submit(task);
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }
    }

    private SiteFixture createSiteWithProcess(String email, String siteName) throws Exception {
        String ownerToken = signup("자재 현장 소유자", email);
        MvcResult siteResult = mockMvc.perform(post("/api/sites")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "address": "서울특별시 자재로 1",
                                  "workType": "건축",
                                  "latitude": null,
                                  "longitude": null,
                                  "companyId": null
                                }
                                """.formatted(siteName)))
                .andExpect(status().isCreated())
                .andReturn();

        long siteId = readNumber(siteResult, "$.id").longValue();
        String joinCode = readString(siteResult, "$.joinCode");

        mockMvc.perform(post("/api/sites/{siteId}/processes", siteId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "FOUNDATION",
                                  "name": "기초 공사",
                                  "weight": 100.00,
                                  "planStart": null,
                                  "planEnd": null,
                                  "sortOrder": 0
                                }
                                """))
                .andExpect(status().isCreated());

        return new SiteFixture(ownerToken, siteId, joinCode);
    }

    private String signup(String name, String email) throws Exception {
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
                .andReturn();
        return readString(result, "$.accessToken");
    }

    private void joinSite(String token, String joinCode) throws Exception {
        mockMvc.perform(post("/api/sites/join")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s"}
                                """.formatted(joinCode)))
                .andExpect(status().isOk());
    }

    private long createMaterialRequest(String token, long siteId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sites/{siteId}/material-requests", siteId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialBody(
                                "FOUNDATION",
                                LocalDate.now().plusDays(7),
                                validItems()
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.events", hasSize(1)))
                .andExpect(jsonPath("$.events[0].status").value("REQUESTED"))
                .andReturn();
        return readNumber(result, "$.id").longValue();
    }

    private void changeStatus(String token, long requestId, String nextStatus) throws Exception {
        mockMvc.perform(patch("/api/material-requests/{requestId}/status", requestId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody(nextStatus, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(nextStatus));
    }

    private String materialBody(String processKey, LocalDate neededBy, String items) {
        return """
                {
                  "processKey": "%s",
                  "neededBy": "%s",
                  "urgent": false,
                  "note": "자동 테스트 자재 요청",
                  "items": %s
                }
                """.formatted(processKey, neededBy, items);
    }

    private String validItems() {
        return "[{\"name\":\"시멘트\",\"quantity\":20.000,\"unit\":\"포\"}]";
    }

    private String statusBody(String status, String rejectReason) {
        String reasonJson = rejectReason == null
                ? "null"
                : "\"" + rejectReason + "\"";
        return """
                {"status":"%s","rejectReason":%s}
                """.formatted(status, reasonJson);
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

    private record SiteFixture(String ownerToken, long siteId, String joinCode) {
    }
}
