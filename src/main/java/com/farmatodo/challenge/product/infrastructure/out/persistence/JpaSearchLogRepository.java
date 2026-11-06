package com.farmatodo.challenge.product.infrastructure.out.persistence;

import java.util.UUID;

import com.farmatodo.challenge.product.domain.ProductPorts.SearchLog;
import com.farmatodo.challenge.product.domain.ProductPorts.SearchLogRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

@Component
class JpaSearchLogRepository implements SearchLogRepository {

    private final SearchLogJpaRepository repository;

    JpaSearchLogRepository(SearchLogJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(SearchLog log) {
        repository.save(new SearchLogJpaEntity(log.query(), (int) Math.min(log.resultsCount(), Integer.MAX_VALUE),
                log.customerId(), log.transactionId(), log.createdAt()));
    }
}

interface SearchLogJpaRepository extends JpaRepository<SearchLogJpaEntity, UUID> {
}
