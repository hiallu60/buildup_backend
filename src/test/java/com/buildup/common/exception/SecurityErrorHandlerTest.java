package com.buildup.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityErrorHandlerTest {

    private ObjectMapper objectMapper;
    private RestAuthenticationEntryPoint authenticationEntryPoint;
    private RestAccessDeniedHandler accessDeniedHandler;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(objectMapper);
        authenticationEntryPoint = new RestAuthenticationEntryPoint(writer);
        accessDeniedHandler = new RestAccessDeniedHandler(writer);
    }

    @Test
    void returnsAuthenticationRequiredWhenTokenIsMissing() throws Exception {
        MockHttpServletRequest request = request("/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        authenticationEntryPoint.commence(
                request,
                response,
                new BadCredentialsException("missing")
        );

        assertThat(response.getStatus()).isEqualTo(401);
        assertStandardBody(response, "AUTHENTICATION_REQUIRED", "/api/users/me");
    }

    @Test
    void returnsInvalidAccessTokenWhenAuthorizationHeaderExists() throws Exception {
        MockHttpServletRequest request = request("/api/users/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        authenticationEntryPoint.commence(
                request,
                response,
                new BadCredentialsException("invalid")
        );

        assertThat(response.getStatus()).isEqualTo(401);
        assertStandardBody(response, "INVALID_ACCESS_TOKEN", "/api/users/me");
    }

    @Test
    void returnsAccessDeniedForForbiddenRequest() throws Exception {
        MockHttpServletRequest request = request("/api/sites/1/join-code");
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(
                request,
                response,
                new AccessDeniedException("forbidden")
        );

        assertThat(response.getStatus()).isEqualTo(403);
        assertStandardBody(response, "ACCESS_DENIED", "/api/sites/1/join-code");
    }

    private MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("GET", path);
    }

    private void assertStandardBody(
            MockHttpServletResponse response,
            String code,
            String path
    ) throws Exception {
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.get("code").asText()).isEqualTo(code);
        assertThat(body.get("path").asText()).isEqualTo(path);
        assertThat(body.get("timestamp")).isNotNull();
        assertThat(body.get("fieldErrors").isObject()).isTrue();
    }
}
