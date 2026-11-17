package com.choco.fulfilment.adapter.out.persistence;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.fulfilment.domain.port.ProductionJobRepository;
import com.choco.fulfilment.domain.production.ProductionJob;
import com.choco.fulfilment.domain.production.ProductionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link ProductionJobRepository}.
 *
 * <p>Each save re-saves the whole aggregate, notes included, because the note collection is cascade-all
 * with orphan removal. That is what makes an added note insert a row and a removed one delete it.
 *
 * <p>Reads are {@code @Transactional(readOnly = true)}: the note collection is lazy and the mapper
 * touches it, so without a transaction the session would already be closed by the time the mapping
 * runs.
 */
@Repository
class ProductionJobRepositoryAdapter implements ProductionJobRepository {

    private static final List<String> SORTABLE = List.of("receivedAt", "updatedAt", "orderNumber",
            "status");

    private final SpringDataProductionJobRepository delegate;

    ProductionJobRepositoryAdapter(SpringDataProductionJobRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public ProductionJob save(ProductionJob job) {
        ProductionJobJpaEntity entity = FulfilmentPersistenceMapper.toEntity(job);
        // The aggregate carries no version, so the mapped entity starts at zero. Carrying the current
        // version across is what makes optimistic locking work.
        delegate.findVersionById(job.id()).ifPresent(entity::setVersion);
        delegate.save(entity);
        return job;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductionJob> findById(UUID id) {
        return delegate.findById(id).map(FulfilmentPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductionJob> findByOrderId(UUID orderId) {
        return delegate.findByOrderIdOrderByReceivedAtAsc(orderId).stream()
                .map(FulfilmentPersistenceMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ProductionJob> findByStatuses(Collection<ProductionStatus> statuses, String search,
                                                    PageQuery page) {
        List<String> names = statuses.stream().map(Enum::name).toList();
        Page<ProductionJobJpaEntity> result = delegate.search(names, likePattern(search), toPageable(page));
        return toPageResult(result, page);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnitsOccupyingCapacity() {
        return delegate.sumUnitsOccupyingCapacity();
    }

    @Override
    public long countUnitsOccupyingCapacityForUpdate() {
        // Locking the rows and summing in the application is how the capacity check stays atomic: a
        // plain sum would read a snapshot two concurrent hand-overs could both act on.
        return delegate.lockOccupyingCapacity().stream()
                .mapToLong(ProductionJobJpaEntity::getQuantity).sum();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(ProductionStatus status) {
        return delegate.countByStatus(status.name());
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return delegate.count();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsForOrderAndVariant(UUID orderId, UUID variantId) {
        return delegate.existsByOrderIdAndVariantId(orderId, variantId);
    }

    private static String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        String escaped = search.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static PageResult<ProductionJob> toPageResult(Page<ProductionJobJpaEntity> result,
                                                          PageQuery query) {
        List<ProductionJob> jobs = result.getContent().stream()
                .map(FulfilmentPersistenceMapper::toDomain).toList();
        return PageResult.of(jobs, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private static PageRequest toPageable(PageQuery query) {
        String sortField = query.sort() == null || !SORTABLE.contains(query.sort()) ? "receivedAt"
                : query.sort();
        Sort.Direction direction = query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(query.page(), query.size(), Sort.by(direction, sortField));
    }
}
