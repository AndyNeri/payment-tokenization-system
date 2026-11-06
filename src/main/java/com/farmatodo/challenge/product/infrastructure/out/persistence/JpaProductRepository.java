package com.farmatodo.challenge.product.infrastructure.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.product.domain.Product;
import com.farmatodo.challenge.product.domain.ProductPorts.PageResult;
import com.farmatodo.challenge.product.domain.ProductPorts.ProductRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
class JpaProductRepository implements ProductRepository {

    private final ProductJpaRepository repository;

    JpaProductRepository(ProductJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public PageResult<Product> search(String query, int minStockExclusive, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<ProductJpaEntity> result = query == null
                ? repository.findByStockGreaterThanOrderByNameAsc(minStockExclusive, pageRequest)
                : repository.search(containsPattern(query), minStockExclusive, pageRequest);
        return new PageResult<>(result.map(ProductJpaEntity::toDomain).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return repository.findById(id).map(ProductJpaEntity::toDomain);
    }

    @Override
    public List<Product> findAllById(Collection<UUID> ids) {
        return repository.findAllById(ids).stream().map(ProductJpaEntity::toDomain).toList();
    }

    @Override
    public boolean decrementStock(UUID id, int quantity) {
        return repository.decrementStock(id, quantity) == 1;
    }

    @Override
    public void incrementStock(UUID id, int quantity) {
        repository.incrementStock(id, quantity);
    }

    /**
     * User input is matched literally: LIKE wildcards are escaped, so searching "%" or "_" does not match
     * everything (and cannot be abused to force expensive scans).
     */
    static String containsPattern(String query) {
        String escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
