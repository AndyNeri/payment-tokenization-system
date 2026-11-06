package com.farmatodo.challenge.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.farmatodo.challenge.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

@IntegrationTest
class CustomerAuthIntegrationTest {

    static final String PASSWORD = "S3gura!2026";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    static String uniquePhone() {
        return "+58412" + String.format("%07d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 10_000_000L));
    }

    static String registration(String email, String phone) {
        return """
                {"firstName":"Andrés","lastName":"Neri","email":"%s","phone":"%s",
                 "address":"Av. Principal 123, Caracas","password":"%s"}""".formatted(email, phone, PASSWORD);
    }

    MvcTestResult register(String json) {
        return mvc.post().uri("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    MvcTestResult login(String email, String password) {
        return mvc.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)).exchange();
    }

    static String json(MvcTestResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
    }

    @Test
    void registersWithNormalisedDataAndNeverReturnsThePassword() throws Exception {
        String email = "Andres." + unique() + "@Mail.COM";

        MvcTestResult result = register(registration(email, "+58 (412) 555-" + unique().substring(0, 4).replaceAll("\\D", "1")));

        assertThat(result).hasStatus(HttpStatus.CREATED).headers().hasValue("Location", "/api/v1/me");
        assertThat(result).bodyJson().extractingPath("$.email").isEqualTo(email.toLowerCase());
        assertThat(result).bodyJson().extractingPath("$.phone").asString().startsWith("+58412555");
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("password").doesNotContain(PASSWORD);

        String hash = jdbc.queryForObject("select password_hash from customers where email = ?", String.class,
                email.toLowerCase());
        assertThat(hash).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
    }

    @Test
    void duplicateEmailIgnoringCaseIs409() {
        String email = unique() + "@mail.com";
        assertThat(register(registration(email, uniquePhone()))).hasStatus(HttpStatus.CREATED);

        assertThat(register(registration(email.toUpperCase(), uniquePhone())))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("EMAIL_ALREADY_EXISTS");
    }

    @Test
    void duplicatePhoneInAnotherFormatIs409() {
        String phone = uniquePhone();
        assertThat(register(registration(unique() + "@mail.com", phone))).hasStatus(HttpStatus.CREATED);
        String reformatted = phone.substring(0, 3) + " " + phone.substring(3, 6) + "-" + phone.substring(6);

        assertThat(register(registration(unique() + "@mail.com", reformatted)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("PHONE_ALREADY_EXISTS");
    }

    @Test
    void invalidDataIs400WithFieldErrors() {
        MvcTestResult result = register("""
                {"firstName":"","lastName":"N","email":"not-an-email","phone":"abc",
                 "address":"x","password":"weak"}""");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
                .contains("firstName", "email", "phone", "address", "password");
    }

    @Test
    void localPhoneWithoutCountryCodeIs400() {
        assertThat(register(registration(unique() + "@mail.com", "0412-1234567")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0].field").isEqualTo("phone");
    }

    @Test
    void concurrentRegistrationsWithSameEmailCreateExactlyOneCustomer() throws Exception {
        String email = "race-" + unique() + "@mail.com";
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String phone = uniquePhone();
            tasks.add(() -> {
                start.await();
                return register(registration(email, phone)).getResponse().getStatus();
            });
        }

        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<Integer>> futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
        }

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(threads - 1);
        assertThat(jdbc.queryForObject("select count(*) from customers where email = ?", Integer.class, email))
                .isEqualTo(1);
    }

    @Test
    void loginReturnsJwtThatUnlocksCustomerEndpoints() throws Exception {
        String email = unique() + "@mail.com";
        String customerId = json(register(registration(email, uniquePhone())), "$.id");

        MvcTestResult login = login(email.toUpperCase(), PASSWORD);
        assertThat(login).hasStatusOk();
        assertThat(login).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
        assertThat(login).bodyJson().extractingPath("$.expiresIn").isEqualTo(1800);
        String token = json(login, "$.accessToken");

        MvcTestResult me = mvc.get().uri("/api/v1/me").header("Authorization", "Bearer " + token).exchange();
        assertThat(me).hasStatusOk();
        assertThat(me).bodyJson().extractingPath("$.id").isEqualTo(customerId);
        assertThat(me).bodyJson().extractingPath("$.email").isEqualTo(email);

        assertThat(mvc.get().uri("/api/v1/me/cards").header("Authorization", "Bearer " + token))
                .hasStatusOk().bodyJson().isEqualTo("[]");
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSame401() throws Exception {
        String email = unique() + "@mail.com";
        register(registration(email, uniquePhone()));

        MvcTestResult wrongPassword = login(email, "Wrong!2026x");
        MvcTestResult unknownEmail = login("ghost-" + unique() + "@mail.com", PASSWORD);

        assertThat(wrongPassword).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(json(wrongPassword, "$.code")).isEqualTo("INVALID_CREDENTIALS").isEqualTo(json(unknownEmail, "$.code"));
        assertThat(json(wrongPassword, "$.detail")).isEqualTo(json(unknownEmail, "$.detail"));
    }

    @Test
    void meForADeletedCustomerIs404() {
        var result = mvc.get().uri("/api/v1/me").with(
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString())));
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("CUSTOMER_NOT_FOUND");
    }
}
