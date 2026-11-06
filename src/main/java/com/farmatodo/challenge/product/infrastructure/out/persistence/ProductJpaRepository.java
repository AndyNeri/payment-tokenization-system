package com.farmatodo.challenge.product.infrastructure.out.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, UUID> {

    String MATCHES = "p.stock > :minStock and (p.name ilike :pattern escape '\\' or p.description ilike :pattern escape '\\')";

    /** Native ILIKE so PostgreSQL can use the pg_trgm GIN indexes on name/description. */
    @Query(value = "select * from products p where " + MATCHES + " order by p.name",
            countQuery = "select count(*) from products p where " + MATCHES,
            nativeQuery = true)
    Page<ProductJpaEntity> search(@Param("pattern") String pattern, @Param("minStock") int minStock, Pageable pageable);

    Page<ProductJpaEntity> findByStockGreaterThanOrderByNameAsc(int minStock, Pageable pageable);

    /** Atomic and conditional: never lets stock go negative, whatever the concurrency. Returns rows updated (0/1). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ProductJpaEntity p set p.stock = p.stock - :quantity, p.version = p.version + 1 "
            + "where p.id = :id and p.stock >= :quantity")
    int decrementStock(@Param("id") UUID id, @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ProductJpaEntity p set p.stock = p.stock + :quantity, p.version = p.version + 1 where p.id = :id")
    int incrementStock(@Param("id") UUID id, @Param("quantity") int quantity);
}
