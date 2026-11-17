package com.choco.order.adapter.out.persistence;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.order.domain.order.Order;
import com.choco.order.domain.order.OrderStatus;
import com.choco.order.domain.port.OrderRepository;
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
 * JPA implementation of {@link OrderRepository}.
 *
 * <p>Each save re-saves the whole aggregate, children included, because the child collections are
 * cascade-all with orphan removal. That is what makes a status-history append actually insert a row,
 * and a removed note actually delete one.
 *
 * <p>Reads are {@code @Transactional(readOnly = true)}: the child collections are lazy and the mapper
 * touches them, so without a transaction the session would already be closed by the time the mapping
 * runs. Opening the transaction here — where the mapping happens — keeps the session alive for exactly
 * as long as it is needed, rather than holding a connection for the whole request via
 * open-session-in-view.
 */
@Repository
class OrderRepositoryAdapter implements OrderRepository {

    private static final List<String> SORTABLE = List.of("createdAt", "updatedAt", "totalMinorUnits",
            "status");

    private final SpringDataOrderRepository delegate;

    OrderRepositoryAdapter(SpringDataOrderRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Order save(Order order) {
        OrderJpaEntity entity = OrderPersistenceMapper.toEntity(order);
        // The aggregate carries no version, so the mapped entity starts at zero. Carrying the current
        // version across is what makes optimistic locking work: the update then carries "where
        // version = N" and a concurrent change fails the check instead of silently overwriting.
        delegate.findVersionById(order.id()).ifPresent(entity::setVersion);
        delegate.save(entity);
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID id) {
        return delegate.findById(id).map(OrderPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByOrderNumber(String orderNumber) {
        return delegate.findByOrderNumber(orderNumber).map(OrderPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> findByCustomer(UUID customerId, PageQuery page) {
        Page<OrderJpaEntity> result = delegate.findByCustomerId(customerId, toPageable(page));
        return toPageResult(result, page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> findByStatuses(Collection<OrderStatus> statuses, UUID customerId,
                                            String search, PageQuery page) {
        List<String> names = statuses.stream().map(Enum::name).toList();
        Page<OrderJpaEntity> result = delegate.search(names, customerId, likePattern(search),
                toPageable(page));
        return toPageResult(result, page);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(OrderStatus status) {
        return delegate.countByStatus(status.name());
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return delegate.count();
    }

    /**
     * Builds a case-insensitive contains-pattern for the search term.
     *
     * <p>LIKE wildcards inside the user's own term are escaped with {@code !} — the escape character
     * declared on the query — so a term containing {@code %} or {@code _} is matched literally. An
     * empty term becomes {@code %} and matches everything, which expresses "no filter" without a
     * nullable parameter.
     */
    private static String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        String escaped = search.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static PageResult<Order> toPageResult(Page<OrderJpaEntity> result, PageQuery query) {
        List<Order> orders = result.getContent().stream().map(OrderPersistenceMapper::toDomain).toList();
        return PageResult.of(orders, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private static PageRequest toPageable(PageQuery query) {
        String sortField = query.sort() == null || !SORTABLE.contains(query.sort()) ? "createdAt"
                : query.sort();
        Sort.Direction direction = query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(query.page(), query.size(), Sort.by(direction, sortField));
    }
}
