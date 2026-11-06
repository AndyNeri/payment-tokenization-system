package com.farmatodo.challenge.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.UUID;

import com.farmatodo.challenge.IntegrationTest;
import com.farmatodo.challenge.TestData;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The minimum stock is configuration, not code: raising it hides "last units" products. */
@IntegrationTest
@TestPropertySource(properties = "app.product.min-stock=3")
class ProductMinStockIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void hidesProductsAtOrBelowConfiguredMinimum() {
        UUID customer = TestData.insertCustomer(jdbc);
        var result = mvc.get().uri("/api/v1/products").param("size", "100")
                .with(jwt().jwt(j -> j.subject(customer.toString())));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.content[*].stock").asArray()
                .allSatisfy(stock -> assertThat((Integer) stock).isGreaterThan(3));
        // seeded with stock 2, 3, 1, 3: visible with min-stock 0, hidden with 3
        assertThat(result).bodyJson().extractingPath("$.content[*].sku").asArray()
                .doesNotContain("MED-0004", "MED-0012", "VIT-0005", "BEB-0004");
    }
}
