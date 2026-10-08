package com.buildup.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthException(
            AuthException exception,
            HttpServletRequest request
    ) {
        if ("INVALID_CREDENTIALS".equals(exception.getCode())) {
            log.warn(
                    "event=auth.login.failed reason=invalid_credentials method={} path={}",
                    request.getMethod(),
                    request.getRequestURI()
            );
        } else {
            logRejected(request, exception.getStatus(), exception.getCode());
        }
        return ResponseEntity
                .status(exception.getStatus())
                .body(ApiErrorResponse.of(
                        exception.getStatus(),
                        exception.getCode(),
                        exception.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(
            ApiException exception,
            HttpServletRequest request
    ) {
        logRejected(request, exception.getStatus(), exception.getCode());
        return ResponseEntity
                .status(exception.getStatus())
                .body(ApiErrorResponse.of(
                        exception.getStatus(),
                        exception.getCode(),
                        exception.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return validationResponse(request, fieldErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getParameterValidationResults().forEach(result -> {
            String parameterName = result.getMethodParameter().getParameterName();
            String key = parameterName == null
                    ? "arg" + result.getMethodParameter().getParameterIndex()
                    : parameterName;
            result.getResolvableErrors().forEach(error ->
                    fieldErrors.putIfAbsent(key, error.getDefaultMessage()));
        });
        return validationResponse(request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                fieldErrors.putIfAbsent(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ));
        return validationResponse(request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        logRejected(request, CommonErrorCode.INVALID_REQUEST_BODY);
        return commonResponse(CommonErrorCode.INVALID_REQUEST_BODY, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return commonValidationResponse(
                CommonErrorCode.INVALID_PARAMETER,
                request,
                Map.of(exception.getName(), "Invalid value")
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request
    ) {
        return commonValidationResponse(
                CommonErrorCode.MISSING_PARAMETER,
                request,
                Map.of(exception.getParameterName(), "Required parameter is missing")
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        logRejected(request, CommonErrorCode.METHOD_NOT_ALLOWED);
        return commonResponse(CommonErrorCode.METHOD_NOT_ALLOWED, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        logRejected(request, CommonErrorCode.API_NOT_FOUND);
        return commonResponse(CommonErrorCode.API_NOT_FOUND, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        log.error(
                "event=database.error code={} method={} path={} exception={}",
                CommonErrorCode.DATA_INTEGRITY_CONFLICT.name(),
                request.getMethod(),
                request.getRequestURI(),
                exception.getClass().getSimpleName()
        );
        return commonResponse(CommonErrorCode.DATA_INTEGRITY_CONFLICT, request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDatabaseException(
            DataAccessException exception,
            HttpServletRequest request
    ) {
        log.error(
                "event=database.error code={} method={} path={} exception={}",
                CommonErrorCode.INTERNAL_SERVER_ERROR.name(),
                request.getMethod(),
                request.getRequestURI(),
                exception.getClass().getSimpleName()
        );
        return commonResponse(CommonErrorCode.INTERNAL_SERVER_ERROR, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
                "event=request.failed code={} method={} path={} exception={} location={}",
                CommonErrorCode.INTERNAL_SERVER_ERROR.name(),
                request.getMethod(),
                request.getRequestURI(),
                exception.getClass().getSimpleName(),
                safeExceptionLocation(exception)
        );
        return commonResponse(CommonErrorCode.INTERNAL_SERVER_ERROR, request);
    }

    private ResponseEntity<ApiErrorResponse> validationResponse(
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        return commonValidationResponse(
                CommonErrorCode.VALIDATION_FAILED,
                request,
                fieldErrors
        );
    }

    private ResponseEntity<ApiErrorResponse> commonResponse(
            CommonErrorCode errorCode,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(errorCode.status())
                .body(ApiErrorResponse.of(
                        errorCode.status(),
                        errorCode.name(),
                        errorCode.message(),
                        request.getRequestURI()
                ));
    }

    private ResponseEntity<ApiErrorResponse> commonValidationResponse(
            CommonErrorCode errorCode,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        HttpStatus status = errorCode.status();
        logRejected(request, status, errorCode.name());
        return ResponseEntity
                .status(status)
                .body(ApiErrorResponse.validation(
                        status,
                        errorCode.name(),
                        errorCode.message(),
                        request.getRequestURI(),
                        fieldErrors
                ));
    }

    private void logRejected(
            HttpServletRequest request,
            CommonErrorCode errorCode
    ) {
        logRejected(request, errorCode.status(), errorCode.name());
    }

    private void logRejected(
            HttpServletRequest request,
            HttpStatus status,
            String code
    ) {
        log.warn(
                "event=request.rejected status={} code={} method={} path={}",
                status.value(),
                code,
                request.getMethod(),
                request.getRequestURI()
        );
    }

    private String safeExceptionLocation(Exception exception) {
        for (StackTraceElement element : exception.getStackTrace()) {
            if (element.getClassName().startsWith("com.buildup.")) {
                return element.getClassName()
                        + "."
                        + element.getMethodName()
                        + ":"
                        + element.getLineNumber();
            }
        }
        return "unknown";
    }
}
