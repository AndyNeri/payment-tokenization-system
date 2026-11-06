package com.farmatodo.challenge.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class ApiKeyAuthenticationFilterTest {

    static final String KEY = "a-very-long-api-key-used-only-in-unit-tests";
    final ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(KEY);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesWithCorrectKey() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(ApiKeyAuthenticationFilter.HEADER, KEY);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly(ApiKeyAuthenticationFilter.ROLE_SYSTEM);
    }

    @Test
    void doesNotAuthenticateWithWrongOrMissingKey() throws Exception {
        var wrong = new MockHttpServletRequest();
        wrong.addHeader(ApiKeyAuthenticationFilter.HEADER, KEY + "x");
        filter.doFilter(wrong, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void comparisonIsExact() {
        assertThat(filter.matches(KEY)).isTrue();
        assertThat(filter.matches(KEY.toUpperCase())).isFalse();
        assertThat(filter.matches(KEY.substring(1))).isFalse();
        assertThat(filter.matches("")).isFalse();
    }
}
