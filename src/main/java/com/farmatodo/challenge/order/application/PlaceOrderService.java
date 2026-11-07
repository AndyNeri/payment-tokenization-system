package com.farmatodo.challenge.order.application;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.farmatodo.challenge.cart.ShoppingCart;
import com.farmatodo.challenge.cart.ShoppingCart.CartLine;
import com.farmatodo.challenge.customer.CustomerDirectory;
import com.farmatodo.challenge.order.OrderEvents.OrderPlaced;
import com.farmatodo.challenge.order.OrderEvents.PaymentAttempted;
import com.farmatodo.challenge.order.OrderEvents.PaymentFailed;
import com.farmatodo.challenge.order.OrderEvents.PaymentSucceeded;
import com.farmatodo.challenge.order.domain.Order;
import com.farmatodo.challenge.order.domain.Order.OrderItem;
import com.farmatodo.challenge.order.domain.Order.PaymentAttempt;
import com.farmatodo.challenge.order.domain.Order.PaymentResult;
import com.farmatodo.challenge.order.domain.OrderPorts.Backoff;
import com.farmatodo.challenge.order.domain.OrderPorts.OrderRepository;
import com.farmatodo.challenge.order.domain.OrderPorts.PaymentGateway;
import com.farmatodo.challenge.order.domain.OrderPorts.PaymentRequest;
import com.farmatodo.challenge.product.ProductInventory;
import com.farmatodo.challenge.product.ProductInventory.ProductSnapshot;
import com.farmatodo.challenge.shared.error.Exceptions.InvalidInputException;
import com.farmatodo.challenge.shared.error.Exceptions.NotFoundException;
import com.farmatodo.challenge.shared.tracing.TransactionContext;
import com.farmatodo.challenge.tokenization.CardLookup;
import com.farmatodo.challenge.tokenization.CardLookup.CardSummary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Checkout: cart → order → payment with retries → final state (see docs/sequences.md §4).
 * <p>
 * Deliberately <b>not</b> one big transaction. Three kinds of short transactions:
 * <ol>
 *   <li><b>T1</b> reserve stock + create the order as PENDING_PAYMENT, committed before charging: the order exists
 *       even if the process dies mid-payment.</li>
 *   <li><b>T2</b> one per payment attempt: every attempt is recorded even if later ones fail. No transaction (and
 *       no DB connection) is held while calling the gateway or waiting between retries.</li>
 *   <li><b>T3</b> final state: PAID (+ empty the cart) or PAYMENT_FAILED (+ give the stock back).</li>
 * </ol>
 * Events are published inside those transactions, so they are stored in the outbox atomically with the change.
 */
@Service
public class PlaceOrderService {

    private static final Logger log = LoggerFactory.getLogger(PlaceOrderService.class);

    private final OrderRepository orders;
    private final ShoppingCart cart;
    private final CardLookup cards;
    private final ProductInventory inventory;
    private final CustomerDirectory customers;
    private final PaymentGateway gateway;
    private final Backoff backoff;
    private final PaymentProperties properties;
    private final ApplicationEventPublisher events;
    private final TransactionOperations tx;
    private final Clock clock;

    PlaceOrderService(OrderRepository orders, ShoppingCart cart, CardLookup cards, ProductInventory inventory,
            CustomerDirectory customers, PaymentGateway gateway, Backoff backoff, PaymentProperties properties,
            ApplicationEventPublisher events, TransactionOperations tx, Clock clock) {
        this.orders = orders;
        this.cart = cart;
        this.cards = cards;
        this.inventory = inventory;
        this.customers = customers;
        this.gateway = gateway;
        this.backoff = backoff;
        this.properties = properties;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    public Order placeOrder(PlaceOrderCommand command) {
        UUID customerId = command.customerId();
        UUID transactionId = TransactionContext.currentOrNew();

        // --- validations (no writes yet) ---
        List<CartLine> lines = cart.linesOf(customerId);
        if (lines.isEmpty()) {
            throw new InvalidInputException("EMPTY_CART", "The cart is empty");
        }
        CardSummary card = cards.findOwned(command.cardToken(), customerId)
                .orElseThrow(() -> new NotFoundException("CARD_NOT_FOUND", "Card not found for this customer"));
        String deliveryAddress = command.deliveryAddress() != null && !command.deliveryAddress().isBlank()
                ? command.deliveryAddress().strip()
                : customers.contactOf(customerId).map(CustomerDirectory.CustomerContact::address)
                        .orElseThrow(() -> new NotFoundException("CUSTOMER_NOT_FOUND", "Customer not found"));
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        lines.forEach(line -> quantities.put(line.productId(), line.quantity()));

        // --- T1: reserve stock + create order (all or nothing) ---
        Order order = tx.execute(status -> {
            Map<UUID, ProductSnapshot> products = inventory.findAll(quantities.keySet());
            List<OrderItem> items = lines.stream().map(line -> {
                ProductSnapshot p = products.get(line.productId());
                if (p == null) {
                    throw new NotFoundException("PRODUCT_NOT_FOUND", "Product " + line.productId() + " no longer exists");
                }
                return new OrderItem(p.id(), p.name(), p.price(), line.quantity());
            }).toList();
            inventory.reserve(quantities);                       // 409 INSUFFICIENT_STOCK rolls everything back
            Order saved = orders.save(Order.place(customerId, card.cardId(), deliveryAddress, items, transactionId,
                    clock.instant()));
            events.publishEvent(new OrderPlaced(saved.id(), customerId, saved.total(), items.size(), transactionId,
                    clock.instant()));
            return saved;
        });
        log.info("Order {} created ({} items, total {}), charging card {}", order.id(), order.items().size(),
                order.total(), card.last4());

        // --- T2 per attempt: charge with retries ---
        PaymentResult result = PaymentResult.REJECTED;
        for (int attempt = 1; attempt <= properties.totalAttempts(); attempt++) {
            if (attempt > 1) {
                backoff.pause(properties.backoffBefore(attempt));
            }
            result = gateway.charge(new PaymentRequest(order.id(), card.token(), order.total(), attempt));
            Order current = order;
            PaymentResult attemptResult = result;
            order = tx.execute(status -> {
                PaymentAttempt recorded = current.recordAttempt(attemptResult, clock.instant());
                events.publishEvent(new PaymentAttempted(current.id(), recorded.attemptNumber(), attemptResult.name(),
                        transactionId, recorded.at()));
                return orders.save(current);
            });
            log.info("Order {} payment attempt {}/{}: {}", order.id(), attempt, properties.totalAttempts(), result);
            if (result == PaymentResult.APPROVED) {
                break;
            }
        }

        // --- T3: final state ---
        Order finalOrder = order;
        boolean approved = result == PaymentResult.APPROVED;
        return tx.execute(status -> {
            int attempts = finalOrder.attempts().size();
            if (approved) {
                finalOrder.markPaid(clock.instant());
                cart.clear(customerId);
                events.publishEvent(new PaymentSucceeded(finalOrder.id(), customerId, finalOrder.total(), attempts,
                        transactionId, clock.instant()));
            } else {
                finalOrder.markPaymentFailed(clock.instant());
                inventory.release(quantities);                  // stock goes back; the cart is kept for a retry
                events.publishEvent(new PaymentFailed(finalOrder.id(), customerId, finalOrder.total(), attempts,
                        transactionId, clock.instant()));
            }
            log.info("Order {} finished as {} after {} attempt(s)", finalOrder.id(), finalOrder.status(), attempts);
            return orders.save(finalOrder);
        });
    }

    public record PlaceOrderCommand(UUID customerId, String cardToken, String deliveryAddress) {
    }
}
