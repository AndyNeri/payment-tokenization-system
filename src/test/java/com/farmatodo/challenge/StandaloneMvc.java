package com.farmatodo.challenge;

import java.util.UUID;

import com.farmatodo.challenge.shared.error.GlobalExceptionHandler;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Controller <b>unit</b> tests: the controller under test plus the real error handler and Bean Validation, without
 * an application context, database or security filter chain. The use cases are mocks.
 */
public final class StandaloneMvc {

    private StandaloneMvc() {
    }

    public static MockMvcTester of(Object... controllers) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcTester.create(MockMvcBuilders.standaloneSetup(controllers)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setValidator(validator)
                .build());
    }

    /** Puts a customer JWT in the security context, as the resource server would after validating it. */
    public static void loggedInAs(UUID customerId) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject(customerId.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    public static void logout() {
        SecurityContextHolder.clearContext();
    }
}
