package com.choco.fulfilment.application.support;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.fulfilment.domain.production.ProductionJob;
import com.choco.fulfilment.domain.production.ProductionStatus;
import com.choco.fulfilment.domain.port.ProductionJobRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory {@link ProductionJobRepository} for unit tests.
 *
 * <p>Keeps real behaviour the aggregate relies on — one job per (order, variant), a capacity total
 * that only counts jobs still occupying the kitchen — so a test asserts on the outcome rather than on
 * which methods were called.
 */
public class InMemoryProductionJobRepository implements ProductionJobRepository {

    private final Map<UUID, ProductionJob> jobs = new LinkedHashMap<>();

    @Override
    public ProductionJob save(ProductionJob job) {
        jobs.put(job.id(), job);
        return job;
    }

    @Override
    public Optional<ProductionJob> findById(UUID id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public List<ProductionJob> findByOrderId(UUID orderId) {
        return jobs.values().stream()
                .filter(job -> job.orderId().equals(orderId))
                .sorted(Comparator.comparing(ProductionJob::receivedAt))
                .toList();
    }

    @Override
    public PageResult<ProductionJob> findByStatuses(Collection<ProductionStatus> statuses, String search,
                                                    PageQuery page) {
        List<ProductionJob> matches = jobs.values().stream()
                .filter(job -> statuses == null || statuses.isEmpty() || statuses.contains(job.status()))
                .filter(job -> matchesSearch(job, search))
                .sorted(Comparator.comparing(ProductionJob::receivedAt))
                .toList();
        return InMemoryPage.of(matches, page);
    }

    @Override
    public long countUnitsOccupyingCapacity() {
        return jobs.values().stream().mapToLong(ProductionJob::unitsOccupyingCapacity).sum();
    }

    @Override
    public long countUnitsOccupyingCapacityForUpdate() {
        return countUnitsOccupyingCapacity();
    }

    @Override
    public long countByStatus(ProductionStatus status) {
        return jobs.values().stream().filter(job -> job.status() == status).count();
    }

    @Override
    public long count() {
        return jobs.size();
    }

    @Override
    public boolean existsForOrderAndVariant(UUID orderId, UUID variantId) {
        return jobs.values().stream()
                .anyMatch(job -> job.orderId().equals(orderId) && job.variantId().equals(variantId));
    }

    private static boolean matchesSearch(ProductionJob job, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        return job.orderNumber().toLowerCase().contains(search.trim().toLowerCase());
    }

    /** Test helper: everything saved, for assertions. */
    public List<ProductionJob> all() {
        return List.copyOf(jobs.values());
    }
}
