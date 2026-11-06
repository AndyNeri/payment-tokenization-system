package com.farmatodo.challenge.tokenization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import com.farmatodo.challenge.IntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
class TokenizationIntegrationTest {

    static final String API_KEY = "test-api-key-0123456789-abcdefghijklmnop";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    UUID customerId;

    @BeforeEach
    void createCustomer() {
        customerId = insertCustomer(jdbc);
    }

    static UUID insertCustomer(JdbcTemplate jdbc) {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 8);
        jdbc.update("insert into customers (id, first_name, last_name, email, phone, address, password_hash) "
                        + "values (?, 'Test', 'User', ?, ?, 'Calle 1', '{bcrypt}x')",
                id, "user-" + suffix + "@test.dev", "+58" + Math.abs(id.getMostSignificantBits() % 1_000_000_000L));
        return id;
    }

    static String body(UUID customerId, String pan, String cvv) {
        return """
                {"customerId":"%s","cardNumber":"%s","cvv":"%s","expirationMonth":12,"expirationYear":2030,
                 "holderName":"Andrés Neri"}""".formatted(customerId, pan, cvv);
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult tokenize(String json) {
        return mvc.post().uri("/api/v1/tokens").header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    @Test
    void tokenizesAndStoresOnlyEncryptedData() throws Exception {
        var result = tokenize(body(customerId, "4111 1111 1111 1111", "123"));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.brand").isEqualTo("VISA");
        assertThat(result).bodyJson().extractingPath("$.last4").isEqualTo("1111");
        assertThat(result).bodyJson().extractingPath("$.token").asString().startsWith("tok_");
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("4111111111111111").doesNotContain("cvv");

        Map<String, Object> row = jdbc.queryForMap("select * from cards where customer_id = ?", customerId);
        assertThat(row).doesNotContainKey("cvv");
        assertThat(row.get("key_id")).isEqualTo("k1");
        assertThat((byte[]) row.get("iv")).hasSize(12);
        String storedPayload = new String((byte[]) row.get("encrypted_payload"), StandardCharsets.ISO_8859_1);
        assertThat(storedPayload).doesNotContain("4111111111111111").doesNotContain("Andrés");
    }

    @Test
    void invalidCardReturns400WithAllFieldErrors() {
        var result = tokenize(body(customerId, TestCards.VISA_BAD_LUHN, "1234"));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_CARD");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactly("cardNumber", "cvv");
    }

    @Test
    void syntacticValidationReturns400() {
        var result = tokenize("""
                {"customerId":null,"cardNumber":"abc","cvv":"1","expirationMonth":13,"expirationYear":2030,"holderName":""}""");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_ERROR");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
                .contains("customerId", "cardNumber", "cvv", "expirationMonth", "holderName");
    }

    @Test
    void malformedJsonReturns400() {
        assertThat(tokenize("{not json")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void unknownCustomerReturns404() {
        assertThat(tokenize(body(UUID.randomUUID(), TestCards.VISA, "123")))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("CUSTOMER_NOT_FOUND");
    }

    @Test
    void customerSeesOnlyOwnMaskedCards() {
        tokenize(body(customerId, TestCards.VISA, "123"));
        tokenize(body(customerId, TestCards.AMEX, "1234"));
        UUID otherCustomer = insertCustomer(jdbc);
        tokenize(body(otherCustomer, TestCards.MASTERCARD, "123"));

        var result = mvc.get().uri("/api/v1/me/cards").with(jwt().jwt(j -> j.subject(customerId.toString())));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[*].brand").asArray().containsExactlyInAnyOrder("VISA", "AMEX");
        assertThat(result).bodyJson().extractingPath("$[*].last4").asArray().containsExactlyInAnyOrder("1111", "0005");
    }

    @Test
    void myCardsRequiresJwt() {
        assertThat(mvc.get().uri("/api/v1/me/cards").header("X-API-Key", API_KEY)).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
