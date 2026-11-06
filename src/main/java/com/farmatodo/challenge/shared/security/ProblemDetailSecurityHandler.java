package com.farmatodo.challenge.shared.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.farmatodo.challenge.shared.error.ProblemDetails;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes 401/403 responses as RFC 9457 ProblemDetail, the same format used by the GlobalExceptionHandler.
 * Needed because security errors happen in the filter chain, before any controller (and its advice) runs.
 */
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String unauthorizedCode;
    private final String unauthorizedDetail;

    public ProblemDetailSecurityHandler(String unauthorizedCode, String unauthorizedDetail) {
        this.unauthorizedCode = unauthorizedCode;
        this.unauthorizedDetail = unauthorizedDetail;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        if (HttpServletRequestUtils.hasBearerToken(request)) {
            response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
        }
        write(request, response, HttpStatus.UNAUTHORIZED, unauthorizedCode, unauthorizedDetail);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to access this resource");
    }

    private static void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
            String code, String detail) throws IOException {
        ProblemDetail problem = ProblemDetails.of(status, code, status.getReasonPhrase(), detail);
        // Serialised as a flat map so the output does not depend on Jackson mix-in registration.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", problem.getType().toString());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        body.put("instance", request.getRequestURI());
        if (problem.getProperties() != null) {
            body.putAll(problem.getProperties());
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.writeValueAsString(body));
    }

    private static final class HttpServletRequestUtils {
        static boolean hasBearerToken(HttpServletRequest request) {
            String header = request.getHeader("Authorization");
            return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7);
        }
    }
}
