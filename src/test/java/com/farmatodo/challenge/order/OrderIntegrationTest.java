package com.farmatodo.challenge.order;

import static com.farmatodo.challenge.order.OrderFixtures.addToCart;
import static com.farmatodo.challenge.order.OrderFixtures.json;
import static com.farmatodo.challenge.order.OrderFixtures.placeOrder;
import static com.farmatodo.challenge.order.OrderFixtures.tokenizeVisa;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.math.BigDecimal;
import java.util.UUID;

import com.farmatodo.challenge.IntegrationTest;
import com.farmatodo.challenge.TestData;
import com.farmatodo.challenge.cart.ShoppingCart;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Checkout with payment probability 0 (always approved): the happy path through the real stack. */
@IntegrationTest
class OrderIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ShoppingCart shoppingCart;

    UUID customer;
    UUID ibuprofen;
    UUID vitamin;
    String cardToken;

    @BeforeEach
    void setUp() throws Exception {
        customer = TestData.insertCustomer(jdbc);
        ibuprofen = TestData.insertProduct(jdbc, "Ibuprofeno pedido", new BigDecimal("4.20"), 10);
        vitamin = TestData.insertProduct(jdbc, "Vitamina pedido", new BigDecimal("8.40"), 5);
        cardToken = tokenizeVisa(mvc, customer);
    }

    @Test
    void checkoutPaysReservesStockSnapshotsPricesAndEmptiesTheCart() throws Exception {
        addToCart(mvc, customer, ibuprofen, 3);
        addToCart(mvc, customer, vitamin, 1);

        MvcTestResult result = placeOrder(mvc, customer, cardToken, "Calle de entrega 45");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        String orderId = json(result, "$.id");
        assertThat(result).headers().hasValue("Location", "/api/v1/orders/" + orderId);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("PAID");
        assertThat(result).bodyJson().extractingPath("$.total").isEqualTo(21.0);
        assertThat(result).bodyJson().extractingPath("$.deliveryAddress").isEqualTo("Calle de entrega 45");
        assertThat(result).bodyJson().extractingPath("$.card.last4").isEqualTo("1111");
        assertThat(result).bodyJson().extractingPath("$.items").asArray().hasSize(2);
        assertThat(result).bodyJson().extractingPath("$.paymentAttempts[0].result").isEqualTo("APPROVED");

        assertThat(TestData.stockOf(jdbc, ibuprofen)).isEqualTo(7);
        assertThat(TestData.stockOf(jdbc, vitamin)).isEqualTo(4);
        assertThat(shoppingCart.linesOf(customer)).isEmpty();

        // the price is frozen: changing the catalog later does not alter the order
        jdbc.update("update products set price = 99.99 where id = ?", ibuprofen);
        MvcTestResult reread = mvc.get().uri("/api/v1/orders/{id}", orderId)
                .with(jwt().jwt(j -> j.subject(customer.toString()))).exchange();
        assertThat(reread).hasStatusOk();
        assertThat(reread).bodyJson().extractingPath("$.total").isEqualTo(21.0);
        assertThat(jdbc.queryForObject("select count(*) from payment_attempts where order_id = ?::uuid",
                Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    void deliveryAddressDefaultsToTheCustomerProfile() {
        addToCart(mvc, customer, vitamin, 1);

        assertThat(placeOrder(mvc, customer, cardToken, null))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.deliveryAddress").isEqualTo("Calle 1");
    }

    @Test
    void emptyCartIs400() {
        assertThat(placeOrder(mvc, customer, cardToken, null))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("EMPTY_CART");
    }

    @Test
    void cardOfAnotherCustomerIs404() throws Exception {
        UUID other = TestData.insertCustomer(jdbc);
        String othersCard = tokenizeVisa(mvc, other);
        addToCart(mvc, customer, vitamin, 1);

        assertThat(placeOrder(mvc, customer, othersCard, null))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("CARD_NOT_FOUND");
        assertThat(TestData.stockOf(jdbc, vitamin)).isEqualTo(5);
    }

    @Test
    void stockThatRanOutAfterAddingToCartIs409AndNothingIsCharged() {
        addToCart(mvc, customer, vitamin, 3);
        jdbc.update("update products set stock = 1 where id = ?", vitamin);   // someone else bought it meanwhile

        assertThat(placeOrder(mvc, customer, cardToken, null))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("INSUFFICIENT_STOCK");
        assertThat(jdbc.queryForObject("select count(*) from orders where customer_id = ?", Integer.class, customer))
                .isZero();
        assertThat(TestData.stockOf(jdbc, vitamin)).isEqualTo(1);
    }

    @Test
    void ordersAreVisibleOnlyToTheirOwner() throws Exception {
        addToCart(mvc, customer, vitamin, 1);
        String orderId = json(placeOrder(mvc, customer, cardToken, null), "$.id");
        UUID stranger = TestData.insertCustomer(jdbc);

        assertThat(mvc.get().uri("/api/v1/orders/{id}", orderId).with(jwt().jwt(j -> j.subject(stranger.toString()))))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("ORDER_NOT_FOUND");
        assertThat(mvc.get().uri("/api/v1/me/orders").with(jwt().jwt(j -> j.subject(customer.toString()))))
                .hasStatusOk()
                .bodyJson().extractingPath("$[*].id").asArray().containsExactly(orderId);
    }

    @Test
    void validationAndAuth() {
        assertThat(mvc.post().uri("/api/v1/orders").with(jwt().jwt(j -> j.subject(customer.toString())))
                .contentType("application/json").content("{\"cardToken\":\"\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_ERROR");
        assertThat(mvc.post().uri("/api/v1/orders").contentType("application/json").content("{}"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
