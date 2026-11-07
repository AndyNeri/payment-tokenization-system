package com.farmatodo.challenge.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.cart.ShoppingCart;
import com.farmatodo.challenge.cart.ShoppingCart.CartLine;
import com.farmatodo.challenge.customer.CustomerDirectory;
import com.farmatodo.challenge.customer.CustomerDirectory.CustomerContact;
import com.farmatodo.challenge.order.OrderEvents.OrderPlaced;
import com.farmatodo.challenge.order.OrderEvents.PaymentAttempted;
import com.farmatodo.challenge.order.OrderEvents.PaymentFailed;
import com.farmatodo.challenge.order.OrderEvents.PaymentSucceeded;
import com.farmatodo.challenge.order.application.PlaceOrderService.PlaceOrderCommand;
import com.farmatodo.challenge.order.domain.Order;
import com.farmatodo.challenge.order.domain.Order.OrderStatus;
import com.farmatodo.challenge.order.domain.Order.PaymentResult;
import com.farmatodo.challenge.order.domain.OrderPorts.OrderRepository;
import com.farmatodo.challenge.order.domain.OrderPorts.PaymentGateway;
import com.farmatodo.challenge.product.InsufficientStockException;
import com.farmatodo.challenge.product.ProductInventory;
import com.farmatodo.challenge.product.ProductInventory.ProductSnapshot;
import com.farmatodo.challenge.shared.error.Exceptions.InvalidInputException;
import com.farmatodo.challenge.shared.error.Exceptions.NotFoundException;
import com.farmatodo.challenge.tokenization.CardLookup;
import com.farmatodo.challenge.tokenization.CardLookup.CardSummary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionOperations;

class PlaceOrderServiceTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"), ZoneOffset.UTC);
    static final UUID CUSTOMER = UUID.randomUUID();
    static final UUID PRODUCT = UUID.randomUUID();
    static final CardSummary CARD = new CardSummary(UUID.randomUUID(), "tok_abc", "VISA", "1111");

    ShoppingCart cart = mock(ShoppingCart.class);
    CardLookup cards = mock(CardLookup.class);
    ProductInventory inventory = mock(ProductInventory.class);
    CustomerDirectory customers = mock(CustomerDirectory.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    InMemoryOrders orders = new InMemoryOrders();
    List<Duration> pauses = new ArrayList<>();
    Deque<PaymentResult> gatewayAnswers = new ArrayDeque<>();
    PaymentGateway gateway = request -> gatewayAnswers.pop();

    PlaceOrderService service(int maxRetries) {
        return new PlaceOrderService(orders, cart, cards, inventory, customers, gateway, pauses::add,
                new PaymentProperties(0.5, maxRetries, Duration.ofMillis(100)), events,
                TransactionOperations.withoutTransaction(), CLOCK);
    }

    @BeforeEach
    void setUp() {
        when(cart.linesOf(CUSTOMER)).thenReturn(List.of(new CartLine(PRODUCT, 2)));
        when(cards.findOwned("tok_abc", CUSTOMER)).thenReturn(Optional.of(CARD));
        when(inventory.findAll(any())).thenReturn(Map.of(PRODUCT,
                new ProductSnapshot(PRODUCT, "MED-1", "Ibuprofeno", new BigDecimal("4.20"), 10)));
        when(customers.contactOf(CUSTOMER)).thenReturn(Optional.of(
                new CustomerContact(CUSTOMER, "Andrés", "Neri", "a@b.c", "Dirección del perfil")));
    }

    static PlaceOrderCommand command(String address) {
        return new PlaceOrderCommand(CUSTOMER, "tok_abc", address);
    }

    List<Object> publishedEvents() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events, org.mockito.Mockito.atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void approvedOnFirstAttempt() {
        gatewayAnswers.add(PaymentResult.APPROVED);

        Order order = service(3).placeOrder(command("Av. Principal 123"));

        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.total()).isEqualByComparingTo("8.40");
        assertThat(order.deliveryAddress()).isEqualTo("Av. Principal 123");
        assertThat(order.attempts()).hasSize(1);
        assertThat(pauses).isEmpty();
        verify(inventory).reserve(Map.of(PRODUCT, 2));
        verify(cart).clear(CUSTOMER);
        verify(inventory, never()).release(any());
        assertThat(publishedEvents()).hasExactlyElementsOfTypes(OrderPlaced.class, PaymentAttempted.class,
                PaymentSucceeded.class);
    }

    @Test
    void retriesWithExponentialBackoffUntilApproved() {
        gatewayAnswers.addAll(List.of(PaymentResult.REJECTED, PaymentResult.REJECTED, PaymentResult.APPROVED));

        Order order = service(3).placeOrder(command(null));

        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.attempts()).extracting(Order.PaymentAttempt::result)
                .containsExactly(PaymentResult.REJECTED, PaymentResult.REJECTED, PaymentResult.APPROVED);
        assertThat(pauses).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200));
        assertThat(order.deliveryAddress()).isEqualTo("Dirección del perfil");   // default from profile
        PaymentSucceeded succeeded = (PaymentSucceeded) publishedEvents().getLast();
        assertThat(succeeded.attempts()).isEqualTo(3);
    }

    @Test
    void allAttemptsRejectedReleasesStockAndKeepsTheCart() {
        gatewayAnswers.addAll(List.of(PaymentResult.REJECTED, PaymentResult.REJECTED, PaymentResult.REJECTED));

        Order order = service(2).placeOrder(command(null));

        assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order.attempts()).hasSize(3);                                  // 1 + maxRetries
        assertThat(pauses).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200));
        verify(inventory).release(Map.of(PRODUCT, 2));
        verify(cart, never()).clear(any());
        assertThat(publishedEvents().getLast()).isInstanceOfSatisfying(PaymentFailed.class,
                e -> assertThat(e.attempts()).isEqualTo(3));
    }

    @Test
    void emptyCartIs400AndNothingHappens() {
        when(cart.linesOf(CUSTOMER)).thenReturn(List.of());

        assertThatThrownBy(() -> service(3).placeOrder(command(null)))
                .isInstanceOf(InvalidInputException.class)
                .hasFieldOrPropertyWithValue("code", "EMPTY_CART");
        verifyNoInteractions(inventory, events);
    }

    @Test
    void someoneElsesCardIs404() {
        when(cards.findOwned("tok_abc", CUSTOMER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(3).placeOrder(command(null)))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "CARD_NOT_FOUND");
        verifyNoInteractions(inventory, events);
    }

    @Test
    void insufficientStockAbortsBeforeCharging() {
        org.mockito.Mockito.doThrow(new InsufficientStockException(PRODUCT, "Ibuprofeno", 1))
                .when(inventory).reserve(any());

        assertThatThrownBy(() -> service(3).placeOrder(command(null)))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(orders.saved).isEmpty();
        assertThat(gatewayAnswers).isEmpty();
    }

    @Test
    void productRemovedFromCatalogIs404() {
        when(inventory.findAll(any())).thenReturn(Map.of());

        assertThatThrownBy(() -> service(3).placeOrder(command(null)))
                .hasFieldOrPropertyWithValue("code", "PRODUCT_NOT_FOUND");
    }

    @Test
    void backoffDoublesAndPropertiesAreValidated() {
        PaymentProperties props = new PaymentProperties(0.3, 3, Duration.ofMillis(200));
        assertThat(props.totalAttempts()).isEqualTo(4);
        assertThat(List.of(props.backoffBefore(2), props.backoffBefore(3), props.backoffBefore(4)))
                .containsExactly(Duration.ofMillis(200), Duration.ofMillis(400), Duration.ofMillis(800));
        assertThat(new PaymentProperties(0.3, 3, Duration.ofSeconds(6)).isBackoffReasonable()).isFalse();
        assertThat(new PaymentProperties(0.3, 3, Duration.ofMillis(-1)).isBackoffReasonable()).isFalse();
    }

    /** Minimal fake repository: assigns ids and keeps the latest version of each order. */
    static class InMemoryOrders implements OrderRepository {
        final Map<UUID, Order> saved = new java.util.LinkedHashMap<>();

        @Override
        public Order save(Order order) {
            Order withId = order.id() == null ? order.withId(UUID.randomUUID()) : order;
            saved.put(withId.id(), withId);
            return withId;
        }

        @Override
        public Optional<Order> findById(UUID id) {
            return Optional.ofNullable(saved.get(id));
        }

        @Override
        public List<Order> findByCustomer(UUID customerId) {
            return saved.values().stream().filter(o -> o.belongsTo(customerId)).toList();
        }
    }
}
