package com.farmatodo.challenge.product.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.product.domain.Product;
import com.farmatodo.challenge.product.domain.ProductPorts.PageResult;
import com.farmatodo.challenge.product.domain.ProductPorts.SearchLog;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/** Product and search-log adapters with Spring Data mocked: mapping, LIKE escaping and stock updates. */
class JpaProductAdaptersTest {

    ProductJpaRepository springData = mock(ProductJpaRepository.class);
    JpaProductRepository products = new JpaProductRepository(springData);
    UUID id = UUID.randomUUID();

    ProductJpaEntity entity() {
        ProductJpaEntity e = new ProductJpaEntity();
        ReflectionTestUtils.setField(e, "id", id);
        ReflectionTestUtils.setField(e, "sku", "MED-1");
        ReflectionTestUtils.setField(e, "name", "Ibuprofeno");
        ReflectionTestUtils.setField(e, "description", "Analgésico");
        ReflectionTestUtils.setField(e, "price", new BigDecimal("4.20"));
        ReflectionTestUtils.setField(e, "stock", 7);
        return e;
    }

    @Test
    void searchWithAndWithoutQuery() {
        when(springData.search(eq("%ibu%"), eq(0), any())).thenReturn(new PageImpl<>(List.of(entity()), PageRequest.of(0, 5), 1));
        when(springData.findByStockGreaterThanOrderByNameAsc(eq(0), any()))
                .thenReturn(new PageImpl<>(List.of(entity()), PageRequest.of(1, 5), 6));

        PageResult<Product> found = products.search("ibu", 0, 0, 5);
        PageResult<Product> all = products.search(null, 0, 1, 5);

        assertThat(found.content()).extracting(Product::name).containsExactly("Ibuprofeno");
        assertThat(found.totalElements()).isEqualTo(1);
        assertThat(all.page()).isEqualTo(1);
        assertThat(all.totalPages()).isEqualTo(2);
    }

    @Test
    void likeWildcardsInTheQueryAreEscaped() {
        assertThat(JpaProductRepository.containsPattern("50%_off\\")).isEqualTo("%50\\%\\_off\\\\%");
    }

    @Test
    void findAndStockUpdates() {
        when(springData.findById(id)).thenReturn(Optional.of(entity()));
        when(springData.findAllById(List.of(id))).thenReturn(List.of(entity()));
        when(springData.decrementStock(id, 2)).thenReturn(1);
        when(springData.decrementStock(id, 99)).thenReturn(0);

        assertThat(products.findById(id)).get().extracting(Product::stock).isEqualTo(7);
        assertThat(products.findAllById(List.of(id))).hasSize(1);
        assertThat(products.decrementStock(id, 2)).isTrue();
        assertThat(products.decrementStock(id, 99)).isFalse();     // not enough stock: 0 rows updated
        products.incrementStock(id, 2);
        verify(springData).incrementStock(id, 2);
    }

    @Test
    void searchLogsAreStored() {
        SearchLogJpaRepository logs = mock(SearchLogJpaRepository.class);
        UUID tx = UUID.randomUUID();

        new JpaSearchLogRepository(logs).save(new SearchLog("ibu", 3, UUID.randomUUID(), tx, Instant.now()));

        ArgumentCaptor<SearchLogJpaEntity> saved = ArgumentCaptor.forClass(SearchLogJpaEntity.class);
        verify(logs).save(saved.capture());
        assertThat(ReflectionTestUtils.getField(saved.getValue(), "transactionId")).isEqualTo(tx);
        assertThat(ReflectionTestUtils.getField(saved.getValue(), "resultsCount")).isEqualTo(3);
    }
}
