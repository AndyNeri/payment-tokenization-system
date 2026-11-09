package com.farmatodo.challenge.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.product.InsufficientStockException;
import com.farmatodo.challenge.product.domain.Product;
import com.farmatodo.challenge.product.domain.ProductPorts.ProductRepository;
import com.farmatodo.challenge.shared.error.Exceptions.NotFoundException;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ProductInventoryServiceTest {

    ProductRepository products = mock(ProductRepository.class);
    ProductInventoryService inventory = new ProductInventoryService(products);
    UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID high = UUID.fromString("00000000-0000-0000-0000-000000000002");
    Product ibuprofen = new Product(low, "MED-1", "Ibuprofeno", "d", new BigDecimal("4.20"), 3);

    @Test
    void findMapsToASnapshot() {
        when(products.findById(low)).thenReturn(Optional.of(ibuprofen));
        when(products.findAllById(List.of(low))).thenReturn(List.of(ibuprofen));

        assertThat(inventory.find(low)).get().satisfies(s -> {
            assertThat(s.name()).isEqualTo("Ibuprofeno");
            assertThat(s.stock()).isEqualTo(3);
        });
        assertThat(inventory.findAll(List.of(low))).containsOnlyKeys(low);
    }

    @Test
    void reservesInIdOrderToAvoidDeadlocks() {
        when(products.decrementStock(low, 1)).thenReturn(true);
        when(products.decrementStock(high, 2)).thenReturn(true);
        Map<UUID, Integer> request = new LinkedHashMap<>();
        request.put(high, 2);                     // given in reverse order on purpose
        request.put(low, 1);

        inventory.reserve(request);

        InOrder order = inOrder(products);
        order.verify(products).decrementStock(low, 1);
        order.verify(products).decrementStock(high, 2);
    }

    @Test
    void notEnoughStockReportsWhatIsAvailable() {
        when(products.decrementStock(low, 5)).thenReturn(false);
        when(products.findById(low)).thenReturn(Optional.of(ibuprofen));

        assertThatThrownBy(() -> inventory.reserve(Map.of(low, 5))).isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Ibuprofeno");
    }

    @Test
    void reservingADeletedProductIs404() {
        when(products.decrementStock(low, 1)).thenReturn(false);
        when(products.findById(low)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventory.reserve(Map.of(low, 1))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void releaseGivesTheStockBack() {
        inventory.release(Map.of(high, 2, low, 1));

        InOrder order = inOrder(products);
        order.verify(products).incrementStock(low, 1);
        order.verify(products).incrementStock(high, 2);
    }
}
