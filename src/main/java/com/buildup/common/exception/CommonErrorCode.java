package com.buildup.common.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "Request body is malformed or unreadable"),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "Request parameter is invalid"),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "Required request parameter is missing"),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "Access token is invalid or expired"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission to access this resource"),
    API_NOT_FOUND(HttpStatus.NOT_FOUND, "API endpoint was not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method is not supported for this endpoint"),
    DATA_INTEGRITY_CONFLICT(HttpStatus.CONFLICT, "Request conflicts with existing data"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected server error occurred");

    private final HttpStatus status;
    private final String message;

    CommonErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
