package com.buildup.common.exception;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        String code,
        String message,
        Instant timestamp,
        Map<String, String> fieldErrors
) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, Instant.now(), Map.of());
    }

    public static ApiErrorResponse validation(Map<String, String> fieldErrors) {
        return new ApiErrorResponse(
                "VALIDATION_FAILED",
                "Request validation failed",
                Instant.now(),
                fieldErrors
        );
    }
}
