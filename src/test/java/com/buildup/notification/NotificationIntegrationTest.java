package com.buildup.notification;

import com.buildup.notification.entity.Notification;
import com.buildup.notification.service.NotificationService;
import com.buildup.user.entity.User;
import com.buildup.user.repository.UserRepository;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class NotificationIntegrationTest {

    private static final String PASSWORD = "BuildUp!234";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void notificationApisKeepUsersIsolatedAndReadOperationsAreIdempotent() throws Exception {
        AuthFixture first = signup("알림 사용자 A", "notification-a@buildup.test");
        AuthFixture second = signup("알림 사용자 B", "notification-b@buildup.test");
        User firstUser = requireUser(first.email());
        User secondUser = requireUser(second.email());

        Notification firstNotification = notificationService.create(
                firstUser, null, "REPORT_APPROVED", "보고서 승인", "승인되었습니다",
                "REPORT", 101L
        );
        notificationService.create(
                firstUser, null, "MATERIAL_REQUEST_APPROVED", "자재 요청 승인", "승인되었습니다",
                "MATERIAL_REQUEST", 201L
        );
        notificationService.create(
                secondUser, null, "REPORT_REJECTED", "보고서 반려", "반려되었습니다",
                "REPORT", 301L
        );

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", bearer(first.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        expectUnreadCount(first.accessToken(), 2);
        expectUnreadCount(second.accessToken(), 1);

        mockMvc.perform(patch("/api/notifications/{notificationId}/read", firstNotification.getId())
                        .header("Authorization", bearer(second.accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

        markRead(first.accessToken(), firstNotification.getId());
        expectUnreadCount(first.accessToken(), 1);

        markRead(first.accessToken(), firstNotification.getId());
        expectUnreadCount(first.accessToken(), 1);

        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", bearer(first.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(1));

        expectUnreadCount(first.accessToken(), 0);
        expectUnreadCount(second.accessToken(), 1);
    }

    @Test
    void notificationReferenceTypeAndIdMustBeProvidedTogether() throws Exception {
        AuthFixture auth = signup("알림 참조 검증", "notification-reference@buildup.test");
        User user = requireUser(auth.email());

        assertThatThrownBy(() -> Notification.create(
                user, null, "TEST", "제목", "내용", "REPORT", null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Notification.create(
                user, null, "TEST", "제목", "내용", null, 1L
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private AuthFixture signup(String name, String email) throws Exception {
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
        return new AuthFixture(
                email,
                JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken")
        );
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    private void expectUnreadCount(String accessToken, long count) throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(count));
    }

    private void markRead(String accessToken, Long notificationId) throws Exception {
        mockMvc.perform(patch("/api/notifications/{notificationId}/read", notificationId)
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty());
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record AuthFixture(String email, String accessToken) {
    }
}
