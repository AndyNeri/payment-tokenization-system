package com.farmatodo.challenge.auth.infrastructure.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.farmatodo.challenge.auth.application.LoginService;
import com.farmatodo.challenge.auth.application.LoginService.AccessToken;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuthController {

    private final LoginService loginService;

    AuthController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/api/v1/auth/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AccessToken token = loginService.login(request.email(), request.password());
        return new LoginResponse(token.value(), "Bearer", token.expiresIn().toSeconds());
    }

    record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 64) String password) {
        @Override
        public String toString() {
            return "LoginRequest[email=" + email + ", password=***]";
        }
    }

    record LoginResponse(String accessToken, String tokenType, long expiresIn) {
    }
}
