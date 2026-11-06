package com.farmatodo.challenge.tokenization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.farmatodo.challenge.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** With rejection probability 1.0 every valid request is rejected: deterministic test of the 422 path. */
@IntegrationTest
@TestPropertySource(properties = "app.tokenization.rejection-probability=1.0")
class TokenizationRejectionIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void rejectedTokenizationReturns422AndStoresNothing() {
        UUID customerId = TokenizationIntegrationTest.insertCustomer(jdbc);

        var result = mvc.post().uri("/api/v1/tokens")
                .header("X-API-Key", TokenizationIntegrationTest.API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(TokenizationIntegrationTest.body(customerId, TestCards.VISA, "123"));

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("TOKENIZATION_REJECTED");
        assertThat(jdbc.queryForObject("select count(*) from cards where customer_id = ?", Integer.class, customerId))
                .isZero();
    }

    @Test
    void invalidDataIsStill400EvenWhenEverythingWouldBeRejected() {
        UUID customerId = TokenizationIntegrationTest.insertCustomer(jdbc);

        assertThat(mvc.post().uri("/api/v1/tokens")
                .header("X-API-Key", TokenizationIntegrationTest.API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(TokenizationIntegrationTest.body(customerId, TestCards.VISA_BAD_LUHN, "123")))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
