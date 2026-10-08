package com.buildup.common.exception;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final ApiErrorResponseWriter errorResponseWriter;

    public RestAuthenticationEntryPoint(ApiErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        CommonErrorCode errorCode = authorization == null || authorization.isBlank()
                ? CommonErrorCode.AUTHENTICATION_REQUIRED
                : CommonErrorCode.INVALID_ACCESS_TOKEN;
        log.warn(
                "event=security.authentication.denied code={} method={} path={}",
                errorCode.name(),
                request.getMethod(),
                request.getRequestURI()
        );
        errorResponseWriter.write(request, response, errorCode);
    }
}
