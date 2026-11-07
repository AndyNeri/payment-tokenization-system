package com.farmatodo.challenge.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.farmatodo.challenge.IntegrationTest;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
class SkeletonIntegrationTest {

    static final String API_KEY = "test-api-key-0123456789-abcdefghijklmnop";
    static final String ISSUER = "payment-tokenization-system";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtEncoder jwtEncoder;

    @Nested
    class Ping {

        @ParameterizedTest
        @ValueSource(strings = {"/ping", "/api/v1/tokens/ping", "/api/v1/customers/ping", "/api/v1/auth/ping",
                "/api/v1/products/ping", "/api/v1/cart/ping", "/api/v1/orders/ping", "/api/v1/audit/ping"})
        void returnsPongWithoutAuthentication(String path) {
            assertThat(mvc.get().uri(path))
                    .hasStatusOk()
                    .hasBodyTextEqualTo("pong");
        }

        @Test
        void healthIsUp() {
            assertThat(mvc.get().uri("/actuator/health"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.status").isEqualTo("UP");
        }
    }

    @Nested
    class Database {

        @Test
        void flywayMigrationsCreateSchemaAndSeedProducts() {
            // Only the seeded catalog: other tests insert their own "TST-" products into the shared database,
            // so counting the whole table would depend on test execution order.
            Integer seeded = jdbc.queryForObject(
                    "select count(*) from products where sku similar to '(MED|VIT|CUI|BEB|PRI)-[0-9]{4}'", Integer.class);
            assertThat(seeded).isEqualTo(40);
        }

        @Test
        void eventPublicationTableExists() {
            Integer rows = jdbc.queryForObject("select count(*) from event_publication", Integer.class);
            assertThat(rows).isNotNull();
        }
    }

    @Nested
    class TransactionId {

        @Test
        void generatesTransactionIdWhenMissing() {
            assertThat(mvc.get().uri("/ping"))
                    .headers().hasHeaderSatisfying("X-Transaction-Id",
                            values -> assertThat(UUID.fromString(values.getFirst())).isNotNull());
        }

        @Test
        void propagatesClientTransactionId() {
            String id = UUID.randomUUID().toString();
            assertThat(mvc.get().uri("/ping").header("X-Transaction-Id", id))
                    .headers().hasValue("X-Transaction-Id", id);
        }
    }

    @Nested
    class ApiKeyChain {

        @Test
        void rejectsMissingApiKeyWithProblemDetail() {
            var result = mvc.post().uri("/api/v1/tokens").contentType(MediaType.APPLICATION_JSON).content("{}");
            assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED)
                    .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_API_KEY");
            assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(401);
            assertThat(result).bodyJson().extractingPath("$.transactionId").isNotNull();
        }

        @Test
        void rejectsWrongApiKey() {
            assertThat(mvc.post().uri("/api/v1/tokens").header("X-API-Key", "wrong-key"))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void acceptsValidApiKey() {
            assertThat(mvc.get().uri("/api/v1/audit").param("transactionId", UUID.randomUUID().toString())
                    .header("X-API-Key", API_KEY))
                    .hasStatusOk();
        }

        @Test
        void jwtIsNotAcceptedOnApiKeyRoutes() {
            assertThat(mvc.post().uri("/api/v1/tokens").header("Authorization", "Bearer " + token(Duration.ofMinutes(5))))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class JwtChain {

        @Test
        void rejectsMissingToken() {
            var result = mvc.get().uri("/api/v1/me");
            assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
        }

        @Test
        void rejectsExpiredToken() {
            assertThat(mvc.get().uri("/api/v1/me").header("Authorization", "Bearer " + token(Duration.ofMinutes(-5))))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .headers().containsHeader("WWW-Authenticate");
        }

        @Test
        void rejectsTamperedToken() {
            String tampered = token(Duration.ofMinutes(5)) + "x";
            assertThat(mvc.get().uri("/api/v1/me").header("Authorization", "Bearer " + tampered))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void apiKeyIsNotAcceptedOnJwtRoutes() {
            assertThat(mvc.get().uri("/api/v1/me").header("X-API-Key", API_KEY))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void acceptsValidTokenAndReturnsProblemDetailForUnknownRoute() {
            UUID customer = com.farmatodo.challenge.TestData.insertCustomer(jdbc);
            var result = mvc.get().uri("/api/v1/does-not-exist")
                    .header("Authorization", "Bearer " + token(customer, Duration.ofMinutes(5)));
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                    .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
            assertThat(result).bodyJson().extractingPath("$.transactionId").isNotNull();
        }

        @Test
        void rejectsValidlySignedTokenWhoseCustomerNoLongerExists() {
            UUID customer = com.farmatodo.challenge.TestData.insertCustomer(jdbc);
            String token = token(customer, Duration.ofMinutes(5));
            assertThat(mvc.get().uri("/api/v1/me").header("Authorization", "Bearer " + token)).hasStatusOk();

            jdbc.update("delete from customers where id = ?", customer);

            var result = mvc.get().uri("/api/v1/me").header("Authorization", "Bearer " + token);
            assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
        }
    }

    private String token(Duration validity) {
        return token(UUID.randomUUID(), validity);
    }

    private String token(UUID subject, Duration validity) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(subject.toString())
                .issuedAt(now.minus(Duration.ofMinutes(10)))
                .expiresAt(now.plus(validity))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
