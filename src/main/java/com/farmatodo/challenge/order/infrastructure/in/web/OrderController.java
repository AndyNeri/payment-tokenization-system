package com.farmatodo.challenge.order.infrastructure.in.web;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.farmatodo.challenge.order.application.OrderQueryService;
import com.farmatodo.challenge.order.application.PlaceOrderService;
import com.farmatodo.challenge.order.application.PlaceOrderService.PlaceOrderCommand;
import com.farmatodo.challenge.order.domain.Order;
import com.farmatodo.challenge.tokenization.CardLookup;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrderController {

    private final PlaceOrderService placeOrder;
    private final OrderQueryService queries;
    private final CardLookup cards;

    OrderController(PlaceOrderService placeOrder, OrderQueryService queries, CardLookup cards) {
        this.placeOrder = placeOrder;
        this.queries = queries;
        this.cards = cards;
    }

    /**
     * Checkout of the whole cart. Answers 201 in both outcomes: the order is created either way, and its
     * {@code status} says whether the payment was approved (PAID) or all attempts were rejected (PAYMENT_FAILED).
     */
    @PostMapping("/api/v1/orders")
    ResponseEntity<OrderResponse> place(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PlaceOrderRequest request) {
        Order order = placeOrder.placeOrder(new PlaceOrderCommand(customerId(jwt), request.cardToken(),
                request.deliveryAddress()));
        return ResponseEntity.created(URI.create("/api/v1/orders/" + order.id())).body(toResponse(order));
    }

    @GetMapping("/api/v1/orders/{orderId}")
    OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return toResponse(queries.getOwned(orderId, customerId(jwt)));
    }

    @GetMapping("/api/v1/me/orders")
    List<OrderResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return queries.ordersOf(customerId(jwt)).stream().map(this::toResponse).toList();
    }

    private OrderResponse toResponse(Order order) {
        CardResponse card = cards.findById(order.cardId())
                .map(c -> new CardResponse(c.brand(), c.last4()))
                .orElse(null);
        return new OrderResponse(order.id(), order.status().name(), order.total(), order.deliveryAddress(), card,
                order.items().stream().map(i -> new ItemResponse(i.productId(), i.productName(), i.unitPrice(),
                        i.quantity(), i.subtotal())).toList(),
                order.attempts().stream().map(a -> new AttemptResponse(a.attemptNumber(), a.result().name(), a.at()))
                        .toList(),
                order.transactionId(), order.createdAt());
    }

    private static UUID customerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record PlaceOrderRequest(@NotBlank @Size(max = 40) String cardToken,
                             @Size(min = 5, max = 255) String deliveryAddress) {
    }

    record OrderResponse(UUID id, String status, BigDecimal total, String deliveryAddress, CardResponse card,
                         List<ItemResponse> items, List<AttemptResponse> paymentAttempts, UUID transactionId,
                         Instant createdAt) {
    }

    record CardResponse(String brand, String last4) {
    }

    record ItemResponse(UUID productId, String name, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {
    }

    record AttemptResponse(int attempt, String result, Instant at) {
    }
}
