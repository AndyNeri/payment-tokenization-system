package com.farmatodo.challenge.auth.infrastructure.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import com.farmatodo.challenge.StandaloneMvc;
import com.farmatodo.challenge.auth.application.LoginService;
import com.farmatodo.challenge.auth.application.LoginService.AccessToken;
import com.farmatodo.challenge.shared.error.Exceptions.UnauthorizedException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class AuthControllerTest {

    LoginService login = mock(LoginService.class);
    MockMvcTester mvc = StandaloneMvc.of(new AuthController(login));

    @Test
    void loginReturnsABearerTokenWithItsLifetime() {
        when(login.login("ana@mail.com", "Secreta123")).thenReturn(new AccessToken("jwt", Duration.ofMinutes(30)));

        var result = mvc.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@mail.com\",\"password\":\"Secreta123\"}");

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
        assertThat(result).bodyJson().extractingPath("$.expiresIn").isEqualTo(1800);
    }

    @Test
    void wrongCredentialsAre401() {
        when(login.login("ana@mail.com", "mala")).thenThrow(new UnauthorizedException("INVALID_CREDENTIALS", "nope"));

        assertThat(mvc.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@mail.com\",\"password\":\"mala\"}"))
                .hasStatus(HttpStatus.UNAUTHORIZED).bodyJson().extractingPath("$.code").isEqualTo("INVALID_CREDENTIALS");
        assertThat(new AuthController.LoginRequest("a@b.c", "Secreta123").toString()).doesNotContain("Secreta123");
    }
}
