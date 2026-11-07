package com.farmatodo.challenge.order;

import static com.farmatodo.challenge.order.OrderFixtures.addToCart;
import static com.farmatodo.challenge.order.OrderFixtures.json;
import static com.farmatodo.challenge.order.OrderFixtures.placeOrder;
import static com.farmatodo.challenge.order.OrderFixtures.tokenizeVisa;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import com.farmatodo.challenge.IntegrationTest;
import com.farmatodo.challenge.TestData;
import com.farmatodo.challenge.cart.ShoppingCart;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Every payment attempt is rejected: the order ends PAYMENT_FAILED after 1 + N attempts. */
@IntegrationTest
@TestPropertySource(properties = {"app.payment.rejection-probability=1.0", "app.payment.max-retries=2"})
class OrderPaymentFailureIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ShoppingCart shoppingCart;

    @Test
    void allAttemptsRejectedLeaveAFailedOrderStockRestoredAndCartIntact() throws Exception {
        UUID customer = TestData.insertCustomer(jdbc);
        UUID product = TestData.insertProduct(jdbc, "Producto rechazado", new BigDecimal("5.00"), 4);
        String card = tokenizeVisa(mvc, customer);
        addToCart(mvc, customer, product, 2);

        MvcTestResult result = placeOrder(mvc, customer, card, null);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("PAYMENT_FAILED");
        assertThat(result).bodyJson().extractingPath("$.paymentAttempts[*].result").asArray()
                .containsExactly("REJECTED", "REJECTED", "REJECTED");          // 1 + max-retries(2)
        assertThat(TestData.stockOf(jdbc, product)).isEqualTo(4);               // released
        assertThat(shoppingCart.linesOf(customer)).hasSize(1);                  // kept, so the customer can retry

        String orderId = json(result, "$.id");
        assertThat(jdbc.queryForObject("select status from orders where id = ?::uuid", String.class, orderId))
                .isEqualTo("PAYMENT_FAILED");
        assertThat(jdbc.queryForObject("select count(*) from payment_attempts where order_id = ?::uuid",
                Integer.class, orderId)).isEqualTo(3);

        // the customer is told by email, and the audit trail shows the failure story under the same transactionId
        String transactionId = result.getResponse().getHeader("X-Transaction-Id");
        assertThat(TestData.awaitCount(jdbc, 1, "select count(*) from audit_events where transaction_id = ?::uuid "
                + "and event_type = 'EMAIL_SENT' and details ->> 'type' = 'PAYMENT_FAILED_EMAIL'", transactionId))
                .isEqualTo(1);
        assertThat(TestData.awaitCount(jdbc, 6, "select count(*) from audit_events where transaction_id = ?::uuid",
                transactionId)).isEqualTo(6);   // ORDER_PLACED + 3 PAYMENT_ATTEMPTED + PAYMENT_FAILED + EMAIL_SENT
    }
}
