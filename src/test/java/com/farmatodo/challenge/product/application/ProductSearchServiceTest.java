package com.farmatodo.challenge.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.farmatodo.challenge.product.ProductsSearched;
import com.farmatodo.challenge.product.domain.ProductPorts.PageResult;
import com.farmatodo.challenge.product.domain.ProductPorts.ProductRepository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class ProductSearchServiceTest {

    ProductRepository repository = mock(ProductRepository.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    Clock clock = Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"), ZoneOffset.UTC);
    ProductSearchService service = new ProductSearchService(repository, new ProductProperties(2), events, clock);

    @Test
    void blankQueryMeansListAllAndUsesConfiguredMinimumStock() {
        when(repository.search(any(), anyInt(), anyInt(), anyInt())).thenReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

        service.search("   ", 0, 20, UUID.randomUUID());

        verify(repository).search(isNull(), eq(2), eq(0), eq(20));
    }

    @Test
    void publishesSearchEventWithResultCount() {
        UUID customer = UUID.randomUUID();
        when(repository.search(eq("ibuprofeno"), anyInt(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 7, 1));

        service.search("  ibuprofeno ", 0, 20, customer);

        ArgumentCaptor<ProductsSearched> event = ArgumentCaptor.forClass(ProductsSearched.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().query()).isEqualTo("ibuprofeno");
        assertThat(event.getValue().resultsCount()).isEqualTo(7);
        assertThat(event.getValue().customerId()).isEqualTo(customer);
        assertThat(event.getValue().occurredAt()).isEqualTo(clock.instant());
    }
}
