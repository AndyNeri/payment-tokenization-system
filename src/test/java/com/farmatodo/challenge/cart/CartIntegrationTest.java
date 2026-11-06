package com.farmatodo.challenge.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.math.BigDecimal;
import java.util.UUID;

import com.farmatodo.challenge.IntegrationTest;
import com.farmatodo.challenge.TestData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class CartIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ShoppingCart shoppingCart;

    UUID customer;
    UUID ibuprofen;
    UUID vitamin;

    @BeforeEach
    void setUp() {
        customer = TestData.insertCustomer(jdbc);
        ibuprofen = TestData.insertProduct(jdbc, "Ibuprofeno test", new BigDecimal("4.20"), 10);
        vitamin = TestData.insertProduct(jdbc, "Vitamina test", new BigDecimal("8.40"), 3);
    }

    MvcTestResult add(UUID who, UUID productId, int quantity) {
        return mvc.post().uri("/api/v1/me/cart/items").with(jwt().jwt(j -> j.subject(who.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"%s\",\"quantity\":%d}".formatted(productId, quantity)).exchange();
    }

    MvcTestResult view(UUID who) {
        return mvc.get().uri("/api/v1/me/cart").with(jwt().jwt(j -> j.subject(who.toString()))).exchange();
    }

    @Test
    void emptyCartForNewCustomer() {
        assertThat(view(customer)).hasStatusOk().bodyJson().isEqualTo("{\"items\":[],\"total\":0}");
    }

    @Test
    void addsMergesAndPricesWithCurrentCatalogPrices() {
        add(customer, ibuprofen, 2);
        add(customer, vitamin, 1);
        MvcTestResult result = add(customer, ibuprofen, 1);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items").asArray().hasSize(2);
        assertThat(result).bodyJson().extractingPath("$.items[0].name").isEqualTo("Ibuprofeno test");
        assertThat(result).bodyJson().extractingPath("$.items[0].quantity").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.items[0].subtotal").isEqualTo(12.6);
        assertThat(result).bodyJson().extractingPath("$.total").isEqualTo(21.0);
    }

    @Test
    void cannotExceedAvailableStockIncludingWhatIsAlreadyInTheCart() {
        assertThat(add(customer, vitamin, 2)).hasStatusOk();

        assertThat(add(customer, vitamin, 2))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("INSUFFICIENT_STOCK");
        assertThat(view(customer)).bodyJson().extractingPath("$.items[0].quantity").isEqualTo(2);
    }

    @Test
    void unknownProductIs404() {
        assertThat(add(customer, UUID.randomUUID(), 1))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    void invalidQuantityIs400() {
        assertThat(add(customer, ibuprofen, 0)).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_ERROR");
        assertThat(add(customer, ibuprofen, 100)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void removesItemsAndReports404ForMissingOnes() {
        add(customer, ibuprofen, 1);
        add(customer, vitamin, 1);

        MvcTestResult removed = mvc.delete().uri("/api/v1/me/cart/items/{id}", ibuprofen)
                .with(jwt().jwt(j -> j.subject(customer.toString()))).exchange();
        assertThat(removed).hasStatusOk();
        assertThat(removed).bodyJson().extractingPath("$.items[*].productId").asArray()
                .containsExactly(vitamin.toString());

        assertThat(mvc.delete().uri("/api/v1/me/cart/items/{id}", ibuprofen)
                .with(jwt().jwt(j -> j.subject(customer.toString()))))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("CART_ITEM_NOT_FOUND");
    }

    @Test
    void eachCustomerHasTheirOwnCart() {
        UUID other = TestData.insertCustomer(jdbc);
        add(customer, ibuprofen, 1);

        assertThat(view(other)).bodyJson().extractingPath("$.items").asArray().isEmpty();
    }

    @Test
    void moduleApiExposesLinesAndClearsCart() {
        add(customer, ibuprofen, 2);

        assertThat(shoppingCart.linesOf(customer)).containsExactly(new ShoppingCart.CartLine(ibuprofen, 2));
        shoppingCart.clear(customer);
        assertThat(shoppingCart.linesOf(customer)).isEmpty();
        assertThat(shoppingCart.linesOf(UUID.randomUUID())).isEmpty();
    }

    @Test
    void requiresJwt() {
        assertThat(mvc.get().uri("/api/v1/me/cart")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
