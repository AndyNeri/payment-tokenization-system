package com.farmatodo.challenge.order.infrastructure.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.StandaloneMvc;
import com.farmatodo.challenge.order.application.OrderQueryService;
import com.farmatodo.challenge.order.application.PlaceOrderService;
import com.farmatodo.challenge.order.application.PlaceOrderService.PlaceOrderCommand;
import com.farmatodo.challenge.order.domain.Order;
import com.farmatodo.challenge.order.domain.Order.OrderItem;
import com.farmatodo.challenge.order.domain.Order.PaymentResult;
import com.farmatodo.challenge.shared.error.Exceptions.NotFoundException;
import com.farmatodo.challenge.tokenization.CardLookup;
import com.farmatodo.challenge.tokenization.CardLookup.CardSummary;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class OrderControllerTest {

    static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

    PlaceOrderService placeOrder = mock(PlaceOrderService.class);
    OrderQueryService queries = mock(OrderQueryService.class);
    CardLookup cards = mock(CardLookup.class);
    MockMvcTester mvc = StandaloneMvc.of(new OrderController(placeOrder, queries, cards));

    UUID customer = UUID.randomUUID();
    UUID cardId = UUID.randomUUID();
    Order paid;

    @BeforeEach
    void setUp() {
        StandaloneMvc.loggedInAs(customer);
        paid = Order.place(customer, cardId, "Calle 1",
                List.of(new OrderItem(UUID.randomUUID(), "Ibuprofeno", new BigDecimal("4.20"), 2)),
                UUID.randomUUID(), NOW).withId(UUID.randomUUID());
        paid.recordAttempt(PaymentResult.REJECTED, NOW);
        paid.recordAttempt(PaymentResult.APPROVED, NOW);
        paid.markPaid(NOW);
        when(cards.findById(cardId)).thenReturn(Optional.of(new CardSummary(cardId, "tok_x", "VISA", "1111")));
    }

    @AfterEach
    void tearDown() {
        StandaloneMvc.logout();
    }

    @Test
    void placeOrderReturns201WithLocationAndTheMaskedCard() {
        when(placeOrder.placeOrder(any(PlaceOrderCommand.class))).thenReturn(paid);

        var result = mvc.post().uri("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardToken\":\"tok_x\",\"deliveryAddress\":\"Calle 1\"}").exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).headers().hasValue("Location", "/api/v1/orders/" + paid.id());
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("PAID");
        assertThat(result).bodyJson().extractingPath("$.card.last4").isEqualTo("1111");
        assertThat(result).bodyJson().extractingPath("$.items[0].subtotal").isEqualTo(8.4);
        assertThat(result).bodyJson().extractingPath("$.paymentAttempts[*].result").asArray()
                .containsExactly("REJECTED", "APPROVED");
    }

    @Test
    void invalidBodyIs400WithoutCallingTheUseCase() {
        assertThat(mvc.post().uri("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardToken\":\"\",\"deliveryAddress\":\"ab\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder("cardToken", "deliveryAddress");
        verifyNoInteractions(placeOrder);
    }

    @Test
    void getOneAndMyOrders() {
        when(queries.getOwned(paid.id(), customer)).thenReturn(paid);
        when(queries.ordersOf(customer)).thenReturn(List.of(paid));
        when(cards.findById(cardId)).thenReturn(Optional.empty());       // card deleted: still renders

        assertThat(mvc.get().uri("/api/v1/orders/{id}", paid.id())).hasStatusOk()
                .bodyJson().extractingPath("$.card").isNull();
        assertThat(mvc.get().uri("/api/v1/me/orders")).hasStatusOk()
                .bodyJson().extractingPath("$[0].id").isEqualTo(paid.id().toString());
    }

    @Test
    void otherCustomersOrderIs404() {
        UUID id = UUID.randomUUID();
        when(queries.getOwned(id, customer)).thenThrow(new NotFoundException("ORDER_NOT_FOUND", "not found"));

        assertThat(mvc.get().uri("/api/v1/orders/{id}", id)).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("ORDER_NOT_FOUND");
    }
}
