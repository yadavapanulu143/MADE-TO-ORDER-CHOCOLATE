package com.choco.fulfilment.domain.port;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.fulfilment.domain.production.ProductionJob;
import com.choco.fulfilment.domain.production.ProductionStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for production jobs.
 *
 * <p>Queries are expressed in business terms — "the preparation queue", "this order's jobs" — rather
 * than in SQL, so the shape of a query is a domain decision and the adapter stays a translation layer.
 */
public interface ProductionJobRepository {

    ProductionJob save(ProductionJob job);

    Optional<ProductionJob> findById(UUID id);

    /** Every job for one order, oldest first, so the seller sees the order's work in one place. */
    java.util.List<ProductionJob> findByOrderId(UUID orderId);

    /** A page of jobs in any of the given statuses, for the preparation queue and the dashboard. */
    PageResult<ProductionJob> findByStatuses(Collection<ProductionStatus> statuses, String search,
                                             PageQuery page);

    /**
     * The total number of units still occupying the kitchen.
     *
     * <p>Used by the capacity check. A database-side sum rather than a load-everything count, because
     * the whole point is to run it inside the same transaction that accepts a new job, where the
     * number of rows must not matter.
     */
    long countUnitsOccupyingCapacity();

    /**
     * The same total, taken under a lock so two hand-overs cannot both see room and both take it.
     *
     * <p>The plain count is fine for a dashboard; the capacity check must use this one, because it
     * decides whether an order is accepted. Without the lock two confirmed orders arriving at the same
     * moment could each read a count below the cap and each be accepted, taking the kitchen past what
     * the seller can make.
     */
    long countUnitsOccupyingCapacityForUpdate();

    long countByStatus(ProductionStatus status);

    long count();

    /** Whether a job already exists for this order line, so a redelivered event cannot duplicate it. */
    boolean existsForOrderAndVariant(UUID orderId, UUID variantId);
}
