package com.choco.fulfilment.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataProductionJobRepository extends JpaRepository<ProductionJobJpaEntity, UUID> {

    Optional<ProductionJobJpaEntity> findByOrderIdAndVariantId(UUID orderId, UUID variantId);

    List<ProductionJobJpaEntity> findByOrderIdOrderByReceivedAtAsc(UUID orderId);

    boolean existsByOrderIdAndVariantId(UUID orderId, UUID variantId);

    long countByStatus(String status);

    /**
     * Reads only the optimistic-lock version of a row.
     *
     * <p>The save path builds a fresh entity from the aggregate and has to carry the current version
     * across so Hibernate compares against the row as it was loaded.
     */
    @Query("select j.version from ProductionJobJpaEntity j where j.id = :id")
    Optional<Long> findVersionById(@Param("id") UUID id);

    /**
     * Locks every job still occupying the kitchen, so a capacity check is atomic with the hand-over
     * that reads it.
     *
     * <p>{@code PESSIMISTIC_WRITE} serialises concurrent hand-overs: two confirmed orders arriving at
     * the same moment cannot both read a total below the cap and both be accepted.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select j from ProductionJobJpaEntity j
            where j.status in ('RECEIVED', 'AWAITING_PREPARATION', 'PREPARATION_STARTED', 'ON_HOLD',
                               'PREPARATION_COMPLETED', 'PACKAGING')
            """)
    List<ProductionJobJpaEntity> lockOccupyingCapacity();

    @Query("""
            select coalesce(sum(j.quantity), 0) from ProductionJobJpaEntity j
            where j.status in ('RECEIVED', 'AWAITING_PREPARATION', 'PREPARATION_STARTED', 'ON_HOLD',
                               'PREPARATION_COMPLETED', 'PACKAGING')
            """)
    long sumUnitsOccupyingCapacity();

    @Query("""
            select j from ProductionJobJpaEntity j
            where j.status in :statuses
              and lower(j.orderNumber) like :pattern escape '!'
            """)
    Page<ProductionJobJpaEntity> search(@Param("statuses") Collection<String> statuses,
                                        @Param("pattern") String pattern, Pageable pageable);
}
