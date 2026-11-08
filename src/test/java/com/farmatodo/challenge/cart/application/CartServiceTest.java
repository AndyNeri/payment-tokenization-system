package com.farmatodo.challenge.cart.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.farmatodo.challenge.cart.ShoppingCart.CartLine;
import com.farmatodo.challenge.cart.domain.Cart;
import com.farmatodo.challenge.cart.domain.Cart.CartRepository;
import com.farmatodo.challenge.product.InsufficientStockException;
import com.farmatodo.challenge.product.ProductInventory;
import com.farmatodo.challenge.product.ProductInventory.ProductSnapshot;
import com.farmatodo.challenge.shared.error.Exceptions.NotFoundException;

import org.junit.jupiter.api.Test;

/** Cart use cases with an in-memory repository and catalog: no Spring, no database. */
class CartServiceTest {

    UUID customer = UUID.randomUUID();
    ProductSnapshot ibuprofen = new ProductSnapshot(UUID.randomUUID(), "MED-1", "Ibuprofeno", new BigDecimal("4.20"), 5);
    ProductSnapshot aspirin = new ProductSnapshot(UUID.randomUUID(), "MED-2", "Aspirina", new BigDecimal("2.50"), 10);

    Map<UUID, Cart> stored = new HashMap<>();
    Map<UUID, ProductSnapshot> catalog = new HashMap<>(Map.of(ibuprofen.id(), ibuprofen, aspirin.id(), aspirin));

    CartRepository repository = new CartRepository() {
        @Override
        public Optional<Cart> findByCustomer(UUID customerId) {
            return Optional.ofNullable(stored.get(customerId));
        }

        @Override
        public Cart save(Cart cart) {
            stored.put(cart.customerId(), cart);
            return cart;
        }
    };

    ProductInventory inventory = new ProductInventory() {
        @Override
        public Optional<ProductSnapshot> find(UUID productId) {
            return Optional.ofNullable(catalog.get(productId));
        }

        @Override
        public Map<UUID, ProductSnapshot> findAll(Collection<UUID> ids) {
            return ids.stream().filter(catalog::containsKey).collect(Collectors.toMap(id -> id, catalog::get));
        }

        @Override
        public void reserve(Map<UUID, Integer> quantities) {
        }

        @Override
        public void release(Map<UUID, Integer> quantities) {
        }
    };

    CartService service = new CartService(repository, inventory);

    @Test
    void emptyCartWhenTheCustomerHasNone() {
        assertThat(service.view(customer).items()).isEmpty();
        assertThat(service.view(customer).total()).isEqualByComparingTo("0");
        assertThat(service.linesOf(customer)).isEmpty();
    }

    @Test
    void addingPricesLinesWithTheCatalogSortedByName() {
        service.add(customer, ibuprofen.id(), 2);
        CartService.CartView view = service.add(customer, aspirin.id(), 3);

        assertThat(view.items()).extracting(CartService.CartView.Line::name).containsExactly("Aspirina", "Ibuprofeno");
        assertThat(view.items().get(1).subtotal()).isEqualByComparingTo("8.40");
        assertThat(view.total()).isEqualByComparingTo("15.90");      // 3 × 2.50 + 2 × 4.20
        assertThat(service.linesOf(customer)).containsExactlyInAnyOrder(
                new CartLine(ibuprofen.id(), 2), new CartLine(aspirin.id(), 3));
    }

    @Test
    void stockIsCheckedAgainstTheAccumulatedQuantity() {
        service.add(customer, ibuprofen.id(), 4);

        assertThatThrownBy(() -> service.add(customer, ibuprofen.id(), 2))   // 4 + 2 > 5 in stock
                .isInstanceOf(InsufficientStockException.class);
        assertThat(service.linesOf(customer)).containsExactly(new CartLine(ibuprofen.id(), 4));
    }

    @Test
    void unknownProductIs404() {
        assertThatThrownBy(() -> service.add(customer, UUID.randomUUID(), 1))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("does not exist");
    }

    @Test
    void productsRemovedFromTheCatalogDisappearFromTheView() {
        service.add(customer, ibuprofen.id(), 1);
        service.add(customer, aspirin.id(), 1);
        catalog.remove(aspirin.id());

        assertThat(service.view(customer).items()).extracting(CartService.CartView.Line::name).containsExactly("Ibuprofeno");
    }

    @Test
    void removeAndClear() {
        service.add(customer, ibuprofen.id(), 1);
        service.add(customer, aspirin.id(), 1);

        assertThat(service.remove(customer, aspirin.id()).items()).hasSize(1);
        service.clear(customer);
        assertThat(service.linesOf(customer)).isEmpty();
        service.clear(UUID.randomUUID());                                    // no cart: nothing to do
    }

    @Test
    void removingFromACustomerWithoutCartIs404() {
        assertThatThrownBy(() -> service.remove(customer, ibuprofen.id()))
                .isInstanceOf(NotFoundException.class);
    }
}
