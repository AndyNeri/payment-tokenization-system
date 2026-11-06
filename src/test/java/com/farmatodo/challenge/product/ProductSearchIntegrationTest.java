package com.farmatodo.challenge.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.farmatodo.challenge.IntegrationTest;
import com.farmatodo.challenge.TestData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class ProductSearchIntegrationTest {

    /** Seed: 40 products, 3 with stock 0. With min-stock = 0 (default) 37 are visible. */
    static final int SEEDED_WITH_STOCK = 37;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    UUID customerId;

    @BeforeEach
    void setUp() {
        customerId = TestData.insertCustomer(jdbc);
    }

    MvcTestResult search(String query) {
        var request = mvc.get().uri("/api/v1/products").with(jwt().jwt(j -> j.subject(customerId.toString())));
        if (query != null) {
            request = request.param("q", query);
        }
        return request.exchange();
    }

    @Test
    void listsOnlyProductsAboveMinimumStockOrderedByName() {
        MvcTestResult result = mvc.get().uri("/api/v1/products").param("size", "100")
                .with(jwt().jwt(j -> j.subject(customerId.toString()))).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.totalElements").asNumber().satisfies(
                total -> assertThat(total.intValue()).isGreaterThanOrEqualTo(SEEDED_WITH_STOCK));
        assertThat(result).bodyJson().extractingPath("$.content[*].stock").asArray()
                .allSatisfy(stock -> assertThat((Integer) stock).isPositive());
        assertThat(result).bodyJson().extractingPath("$.content[*].sku").asArray()
                .doesNotContain("MED-0008", "CUI-0007", "PRI-0007");   // seeded with stock 0
    }

    @Test
    void searchIsCaseInsensitiveOnNameAndDescription() {
        assertThat(search("ACETAMINOF")).bodyJson().extractingPath("$.totalElements").isEqualTo(2);
        assertThat(search("antiséptico")).bodyJson().extractingPath("$.content[*].sku").asArray()
                .contains("PRI-0002", "PRI-0005");
    }

    @Test
    void wildcardsInTheQueryAreMatchedLiterally() {
        // Unescaped, "%" would match the whole catalog. Escaped, it only matches a literal "%" ("alcohol al 70%").
        assertThat(search("%")).bodyJson().extractingPath("$.content[*].sku").asArray()
                .containsExactlyInAnyOrder("CUI-0003", "PRI-0002");
        assertThat(search("_")).bodyJson().extractingPath("$.totalElements").isEqualTo(0);
    }

    @Test
    void paginates() {
        MvcTestResult page = mvc.get().uri("/api/v1/products").param("page", "1").param("size", "5")
                .with(jwt().jwt(j -> j.subject(customerId.toString()))).exchange();

        assertThat(page).bodyJson().extractingPath("$.page").isEqualTo(1);
        assertThat(page).bodyJson().extractingPath("$.content").asArray().hasSize(5);
    }

    @Test
    void invalidPaginationIs400() {
        assertThat(mvc.get().uri("/api/v1/products").param("size", "500")
                .with(jwt().jwt(j -> j.subject(customerId.toString()))))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void requiresJwt() {
        assertThat(mvc.get().uri("/api/v1/products")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void searchIsStoredAsynchronouslyWithItsTransactionId() throws Exception {
        UUID transactionId = UUID.randomUUID();
        MvcTestResult result = mvc.get().uri("/api/v1/products").param("q", "vitamina")
                .header("X-Transaction-Id", transactionId.toString())
                .with(jwt().jwt(j -> j.subject(customerId.toString()))).exchange();
        assertThat(result).hasStatusOk();
        int total = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.totalElements");

        Integer stored = awaitCount("select count(*) from search_logs where transaction_id = ? and query = 'vitamina' "
                + "and customer_id = ? and results_count = ?", transactionId, customerId, total);
        assertThat(stored).isEqualTo(1);
        // the outbox entry is marked completed once the listener finished
        assertThat(awaitCount("select count(*) from event_publication where completion_date is not null "
                + "and serialized_event like ?", "%" + transactionId + "%")).isEqualTo(1);
    }

    private Integer awaitCount(String sql, Object... args) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        Integer count = 0;
        while (Instant.now().isBefore(deadline)) {
            count = jdbc.queryForObject(sql, Integer.class, args);
            if (count != null && count > 0) {
                return count;
            }
            Thread.sleep(100);
        }
        return count;
    }
}
