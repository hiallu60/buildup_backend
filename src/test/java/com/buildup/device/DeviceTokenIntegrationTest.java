package com.buildup.device;

import com.buildup.device.entity.DeviceToken;
import com.buildup.device.repository.DeviceTokenRepository;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class DeviceTokenIntegrationTest {

    private static final String PASSWORD = "BuildUp!234";
    private static final String DEVICE_TOKEN = "test-device-token-001";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        deviceTokenRepository.deleteAll();
    }

    @Test
    void registrationRefreshesTimestampAndMovesTokenOwnership() throws Exception {
        AuthFixture first = signup("기기 사용자 A", "device-a@buildup.test");
        AuthFixture second = signup("기기 사용자 B", "device-b@buildup.test");

        register(first.accessToken(), DEVICE_TOKEN);
        DeviceToken initiallyRegistered = requireToken(DEVICE_TOKEN);
        LocalDateTime initialUpdatedAt = initiallyRegistered.getUpdatedAt();
        assertThat(initiallyRegistered.getUser().getId()).isEqualTo(requireUser(first.email()).getId());

        Thread.sleep(2L);
        register(first.accessToken(), "  " + DEVICE_TOKEN + "  ");
        DeviceToken refreshed = requireToken(DEVICE_TOKEN);
        assertThat(refreshed.getUpdatedAt()).isAfter(initialUpdatedAt);

        register(second.accessToken(), DEVICE_TOKEN);
        DeviceToken reassigned = requireToken(DEVICE_TOKEN);
        assertThat(deviceTokenRepository.count()).isEqualTo(1);
        assertThat(reassigned.getUser().getId()).isEqualTo(requireUser(second.email()).getId());
    }

    @Test
    void onlyCurrentOwnerCanRemoveTokenAndBlankTokenIsRejected() throws Exception {
        AuthFixture owner = signup("기기 소유자", "device-owner@buildup.test");
        AuthFixture outsider = signup("기기 외부 사용자", "device-outsider@buildup.test");
        register(owner.accessToken(), DEVICE_TOKEN);

        remove(outsider.accessToken(), DEVICE_TOKEN);
        assertThat(deviceTokenRepository.findByToken(DEVICE_TOKEN)).isPresent();

        remove(owner.accessToken(), DEVICE_TOKEN);
        assertThat(deviceTokenRepository.findByToken(DEVICE_TOKEN)).isEmpty();

        mockMvc.perform(post("/api/devices/tokens")
                        .header("Authorization", bearer(owner.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"   "}
                                """))
                .andExpect(status().isBadRequest());
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

    private DeviceToken requireToken(String token) {
        return deviceTokenRepository.findByToken(token).orElseThrow();
    }

    private void register(String accessToken, String token) throws Exception {
        mockMvc.perform(post("/api/devices/tokens")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(token)))
                .andExpect(status().isNoContent());
    }

    private void remove(String accessToken, String token) throws Exception {
        mockMvc.perform(delete("/api/devices/tokens")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(token)))
                .andExpect(status().isNoContent());
    }

    private String tokenBody(String token) {
        return "{\"token\":\"" + token + "\"}";
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record AuthFixture(String email, String accessToken) {
    }
}
