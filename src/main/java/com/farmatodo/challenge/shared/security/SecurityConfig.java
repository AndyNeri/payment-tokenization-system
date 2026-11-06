package com.farmatodo.challenge.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Two security filter chains:
 * <ol>
 *   <li><b>API key chain</b> (machine-to-machine): card tokenization and audit queries.</li>
 *   <li><b>Default chain</b>: public endpoints (ping, register, login, health, docs) + JWT for everything else.</li>
 * </ol>
 * Both are stateless (no HTTP session) so the service scales horizontally on Cloud Run. CSRF protection is
 * disabled because no cookies/sessions are used: credentials travel in headers that a browser never sends
 * automatically.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    // Exact paths on purpose: /api/v1/tokens/ping and /api/v1/audit/ping must stay public.
    static final String[] API_KEY_PATHS = {"/api/v1/tokens", "/api/v1/audit"};

    static final String[] PUBLIC_GET_PATHS = {
            "/ping", "/api/v1/*/ping",
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    static final String[] PUBLIC_POST_PATHS = {"/api/v1/customers", "/api/v1/auth/login"};

    @Bean
    @Order(1)
    SecurityFilterChain apiKeyChain(HttpSecurity http, SecurityProperties properties) throws Exception {
        var errors = new ProblemDetailSecurityHandler("INVALID_API_KEY", "Missing or invalid X-API-Key header");
        return stateless(http)
                .securityMatcher(API_KEY_PATHS)
                .addFilterBefore(new ApiKeyAuthenticationFilter(properties.apiKey()), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(ApiKeyAuthenticationFilter.ROLE_SYSTEM))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
        var errors = new ProblemDetailSecurityHandler("UNAUTHORIZED", "Missing, invalid or expired access token");
        return stateless(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS).permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .build();
    }

    /** BCrypt by default, behind a delegating encoder ({bcrypt}...) so the algorithm can evolve (e.g. Argon2). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private static HttpSecurity stateless(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }
}
