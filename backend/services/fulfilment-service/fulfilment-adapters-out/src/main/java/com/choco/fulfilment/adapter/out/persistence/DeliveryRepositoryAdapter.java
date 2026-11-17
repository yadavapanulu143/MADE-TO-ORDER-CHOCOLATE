package com.choco.fulfilment.adapter.out.persistence;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.fulfilment.domain.delivery.Delivery;
import com.choco.fulfilment.domain.delivery.DeliveryStatus;
import com.choco.fulfilment.domain.port.DeliveryRepository;
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
 * JPA implementation of {@link DeliveryRepository}.
 *
 * <p>Attempts are cascade-all with orphan removal, so saving a delivery writes any new attempt with
 * it. Reads open a transaction because the attempt collection is lazy and the mapper touches it.
 */
@Repository
class DeliveryRepositoryAdapter implements DeliveryRepository {

    private static final List<String> SORTABLE = List.of("createdAt", "updatedAt", "status");

    private final SpringDataDeliveryRepository delegate;

    DeliveryRepositoryAdapter(SpringDataDeliveryRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Delivery save(Delivery delivery) {
        DeliveryJpaEntity entity = FulfilmentPersistenceMapper.toEntity(delivery);
        delegate.findVersionById(delivery.id()).ifPresent(entity::setVersion);
        delegate.save(entity);
        return delivery;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Delivery> findById(UUID id) {
        return delegate.findById(id).map(FulfilmentPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Delivery> findByOrderId(UUID orderId) {
        return delegate.findByOrderId(orderId).map(FulfilmentPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Delivery> findByStatuses(Collection<DeliveryStatus> statuses, String search,
                                               PageQuery page) {
        List<String> names = statuses.stream().map(Enum::name).toList();
        Page<DeliveryJpaEntity> result = delegate.search(names, likePattern(search), toPageable(page));
        return toPageResult(result, page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Delivery> findByCustomer(UUID customerId, PageQuery page) {
        Page<DeliveryJpaEntity> result = delegate.findByCustomerId(customerId, toPageable(page));
        return toPageResult(result, page);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(DeliveryStatus status) {
        return delegate.countByStatus(status.name());
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return delegate.count();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsForOrder(UUID orderId) {
        return delegate.existsByOrderId(orderId);
    }

    private static String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        String escaped = search.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static PageResult<Delivery> toPageResult(Page<DeliveryJpaEntity> result, PageQuery query) {
        List<Delivery> deliveries = result.getContent().stream()
                .map(FulfilmentPersistenceMapper::toDomain).toList();
        return PageResult.of(deliveries, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private static PageRequest toPageable(PageQuery query) {
        String sortField = query.sort() == null || !SORTABLE.contains(query.sort()) ? "createdAt"
                : query.sort();
        Sort.Direction direction = query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(query.page(), query.size(), Sort.by(direction, sortField));
    }
}
