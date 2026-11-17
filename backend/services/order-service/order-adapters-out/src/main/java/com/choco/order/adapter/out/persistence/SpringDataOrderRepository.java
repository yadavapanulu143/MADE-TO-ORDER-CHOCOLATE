package com.choco.order.adapter.out.persistence;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataOrderRepository extends JpaRepository<OrderJpaEntity, UUID> {

    Optional<OrderJpaEntity> findByOrderNumber(String orderNumber);

    long countByStatus(String status);

    /**
     * Reads only the optimistic-lock version of a row.
     *
     * <p>Used by the save path, which builds a fresh entity from the aggregate and has to carry the
     * current version across so Hibernate compares against the row as it was loaded. Without this the
     * update would carry version zero against a row already past it and fail as a stale object.
     */
    @Query("select o.version from OrderJpaEntity o where o.id = :id")
    Optional<Long> findVersionById(@Param("id") UUID id);

    Page<OrderJpaEntity> findByCustomerId(UUID customerId, Pageable pageable);

    /**
     * The seller's listing: any of the given statuses, optionally narrowed to one customer and to an
     * order number matching a search term.
     *
     * <p>The search term reaches the database as a bound {@code LIKE} pattern, never concatenated into
     * the query text, so a term containing SQL metacharacters is treated as data. The pattern is
     * always supplied — {@code "%"} when the caller searches for nothing — rather than tested for
     * {@code null} in the query, which made PostgreSQL infer the parameter's type from an
     * {@code IS NULL} comparison and fail on {@code lower(...)}.
     */
    @Query("""
            select o from OrderJpaEntity o
            where o.status in :statuses
              and (:customerId is null or o.customerId = :customerId)
              and lower(o.orderNumber) like :pattern escape '!'
            """)
    Page<OrderJpaEntity> search(@Param("statuses") Collection<String> statuses,
                                @Param("customerId") UUID customerId,
                                @Param("pattern") String pattern, Pageable pageable);
}
