package com.choco.order.domain.port;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.order.domain.order.Order;
import com.choco.order.domain.order.OrderStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for orders.
 *
 * <p>Declared in the domain, implemented by a JPA adapter, so the aggregate never learns that a
 * database exists. Queries are expressed in business terms — "this customer's orders", "orders in
 * these statuses" — rather than in SQL, so the shape of a query is a domain decision.
 */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    Optional<Order> findByOrderNumber(String orderNumber);

    /** A page of one customer's orders, newest first. The ownership filter is part of the query. */
    PageResult<Order> findByCustomer(UUID customerId, PageQuery page);

    /** A page of all orders in any of the given statuses, for the seller's dashboard. */
    PageResult<Order> findByStatuses(Collection<OrderStatus> statuses, UUID customerId, String search,
                                     PageQuery page);

    long countByStatus(OrderStatus status);

    long count();
}
