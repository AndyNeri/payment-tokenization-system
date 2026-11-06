package com.farmatodo.challenge.shared.tracing;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns a transaction id (UUID) to every request. Runs before Spring Security so even
 * authentication errors carry it.
 * <ul>
 *   <li>If the client sends a valid {@code X-Transaction-Id} UUID, it is reused (end-to-end tracing).</li>
 *   <li>Otherwise (missing or malformed) a new one is generated; client input is never trusted blindly.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TransactionIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID transactionId = resolve(request.getHeader(TransactionContext.HEADER));
        TransactionContext.set(transactionId);
        response.setHeader(TransactionContext.HEADER, transactionId.toString());
        try {
            chain.doFilter(request, response);
        } finally {
            TransactionContext.clear();
        }
    }

    static UUID resolve(String headerValue) {
        if (headerValue != null && !headerValue.isBlank()) {
            try {
                return UUID.fromString(headerValue.strip());
            } catch (IllegalArgumentException ignored) {
                // malformed header: fall through and generate a fresh id
            }
        }
        return UUID.randomUUID();
    }
}
