package com.buildup.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsStandardResponseForDomainException() throws Exception {
        mockMvc.perform(get("/test/api-error"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("TEST_CONFLICT"))
                .andExpect(jsonPath("$.message").value("Test conflict"))
                .andExpect(jsonPath("$.path").value("/test/api-error"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").isMap());
    }

    @Test
    void returnsFieldErrorsForRequestBodyValidation() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path").value("/test/validation"))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void returnsInvalidBodyForMalformedJson() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.path").value("/test/validation"));
    }

    @Test
    void returnsInvalidParameterForTypeMismatch() throws Exception {
        mockMvc.perform(get("/test/number").param("value", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.fieldErrors.value").value("Invalid value"));
    }

    @Test
    void returnsMissingParameterForRequiredQueryParameter() throws Exception {
        mockMvc.perform(get("/test/number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.fieldErrors.value").value("Required parameter is missing"));
    }

    @Test
    void returnsMethodNotAllowedForUnsupportedMethod() throws Exception {
        mockMvc.perform(post("/test/number"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void returnsConflictWithoutLeakingDatabaseDetails() throws Exception {
        mockMvc.perform(get("/test/data-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_CONFLICT"))
                .andExpect(jsonPath("$.message").value("Request conflicts with existing data"));
    }

    @Test
    void returnsGenericMessageForUnexpectedException() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected server error occurred"));
    }

    @RestController
    static class TestController {

        @PostMapping("/test/validation")
        String validate(@Valid @RequestBody TestRequest request) {
            return request.name();
        }

        @GetMapping("/test/number")
        int number(@RequestParam Integer value) {
            return value;
        }

        @GetMapping("/test/api-error")
        void apiError() {
            throw new ApiException(HttpStatus.CONFLICT, "TEST_CONFLICT", "Test conflict");
        }

        @GetMapping("/test/data-conflict")
        void dataConflict() {
            throw new DataIntegrityViolationException("do not expose this detail");
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("do not expose this detail");
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
