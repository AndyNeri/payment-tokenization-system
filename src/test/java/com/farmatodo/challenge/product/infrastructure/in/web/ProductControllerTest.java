package com.farmatodo.challenge.product.infrastructure.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.farmatodo.challenge.StandaloneMvc;
import com.farmatodo.challenge.product.application.ProductSearchService;
import com.farmatodo.challenge.product.domain.Product;
import com.farmatodo.challenge.product.domain.ProductPorts.PageResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class ProductControllerTest {

    ProductSearchService service = mock(ProductSearchService.class);
    MockMvcTester mvc = StandaloneMvc.of(new ProductController(service));
    UUID customer = UUID.randomUUID();

    @BeforeEach
    void login() {
        StandaloneMvc.loggedInAs(customer);
    }

    @AfterEach
    void logout() {
        StandaloneMvc.logout();
    }

    @Test
    void searchMapsThePage() {
        Product p = new Product(UUID.randomUUID(), "MED-1", "Ibuprofeno", "Analgésico", new BigDecimal("4.20"), 10);
        when(service.search("ibu", 0, 5, customer)).thenReturn(new PageResult<>(List.of(p), 0, 5, 1, 1));

        assertThat(mvc.get().uri("/api/v1/products").param("q", "ibu").param("size", "5"))
                .hasStatusOk().bodyJson().extractingPath("$.content[0].sku").isEqualTo("MED-1");
    }

    @Test
    void pageSizeAboveTheLimitIs400() {
        assertThat(mvc.get().uri("/api/v1/products").param("size", "1000"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_ERROR");
    }
}
