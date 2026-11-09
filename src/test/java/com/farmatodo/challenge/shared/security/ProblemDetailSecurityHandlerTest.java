package com.farmatodo.challenge.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

/** 401/403 from the security filters use the same RFC 9457 format as the rest of the API. */
class ProblemDetailSecurityHandlerTest {

    ProblemDetailSecurityHandler handler = new ProblemDetailSecurityHandler("UNAUTHORIZED", "Missing token");

    @Test
    void unauthorizedIsAProblemDetailAndSignalsAnInvalidBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader("Authorization", "Bearer broken");
        MockHttpServletResponse response = new MockHttpServletResponse();

        org.slf4j.MDC.put("transactionId", "5f0c0000-0000-4000-8000-000000000001");
        try {
            handler.commence(request, response, new BadCredentialsException("bad"));
        } finally {
            org.slf4j.MDC.clear();
        }

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer error=\"invalid_token\"");
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"", "\"instance\":\"/api/v1/me\"",
                "\"detail\":\"Missing token\"", "\"transactionId\":\"5f0c0000-0000-4000-8000-000000000001\"");
    }

    @Test
    void withoutBearerTokenThereIsNoChallengeHeader() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.commence(new MockHttpServletRequest("GET", "/api/v1/me"), response, new BadCredentialsException("x"));
        assertThat(response.getHeader("WWW-Authenticate")).isNull();
    }

    @Test
    void forbiddenIs403() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(new MockHttpServletRequest("GET", "/x"), response, new AccessDeniedException("no"));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
    }
}
