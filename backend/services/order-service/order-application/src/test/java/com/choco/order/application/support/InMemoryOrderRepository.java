package com.choco.order.application.support;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.order.domain.order.Order;
import com.choco.order.domain.order.OrderStatus;
import com.choco.order.domain.port.OrderRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory order store with the same filtering semantics the JPA adapter will implement. */
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<UUID, Order> byId = new LinkedHashMap<>();

    @Override
    public Order save(Order order) {
        byId.put(order.id(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Order> findByOrderNumber(String orderNumber) {
        return byId.values().stream().filter(o -> o.orderNumber().equals(orderNumber)).findFirst();
    }

    @Override
    public PageResult<Order> findByCustomer(UUID customerId, PageQuery page) {
        List<Order> matching = byId.values().stream()
                .filter(o -> o.customerId().equals(customerId))
                .sorted(Comparator.comparing(Order::createdAt).reversed())
                .toList();
        return paginate(matching, page);
    }

    @Override
    public PageResult<Order> findByStatuses(Collection<OrderStatus> statuses, UUID customerId,
                                            String search, PageQuery page) {
        List<Order> matching = new ArrayList<>(byId.values().stream()
                .filter(o -> statuses.contains(o.status()))
                .filter(o -> customerId == null || o.customerId().equals(customerId))
                .filter(o -> search == null || o.orderNumber().toLowerCase().contains(search.toLowerCase()))
                .sorted(Comparator.comparing(Order::createdAt).reversed())
                .toList());
        return paginate(matching, page);
    }

    @Override
    public long countByStatus(OrderStatus status) {
        return byId.values().stream().filter(o -> o.status() == status).count();
    }

    @Override
    public long count() {
        return byId.size();
    }

    private PageResult<Order> paginate(List<Order> all, PageQuery page) {
        int from = Math.min(page.offset(), all.size());
        int to = Math.min(from + page.size(), all.size());
        return PageResult.of(all.subList(from, to), page.page(), page.size(), all.size());
    }
}
