package com.farmatodo.challenge.shared.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates machine-to-machine calls with the {@code X-API-Key} header.
 * <p>
 * Comparison is constant-time ({@link MessageDigest#isEqual}) so response timing does not reveal how many
 * leading characters of a guessed key are correct. On a missing/wrong key the filter does not authenticate and
 * Spring Security's entry point answers 401 (INVALID_API_KEY).
 * <p>
 * Not a Spring bean on purpose: it is only added to the API-key security chain, never as a global servlet filter.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    public static final String ROLE_SYSTEM = "ROLE_SYSTEM";

    private final byte[] expectedKey;

    public ApiKeyAuthenticationFilter(String expectedKey) {
        this.expectedKey = expectedKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided != null && matches(provided)) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    "api-client", null, List.of(new SimpleGrantedAuthority(ROLE_SYSTEM)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }

    boolean matches(String provided) {
        return MessageDigest.isEqual(expectedKey, provided.getBytes(StandardCharsets.UTF_8));
    }
}
