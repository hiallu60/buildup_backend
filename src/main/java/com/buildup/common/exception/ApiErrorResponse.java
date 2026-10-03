package com.buildup.common.exception;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors
) {

    public ApiErrorResponse {
        fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }

    public static ApiErrorResponse of(
            HttpStatus status,
            String code,
            String message,
            String path
    ) {
        return new ApiErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                path,
                Map.of()
        );
    }

    public static ApiErrorResponse validation(
            HttpStatus status,
            String code,
            String message,
            String path,
            Map<String, String> fieldErrors
    ) {
        return new ApiErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                path,
                fieldErrors
        );
    }
}
