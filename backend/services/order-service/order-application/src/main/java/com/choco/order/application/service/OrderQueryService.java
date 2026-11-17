package com.choco.order.application.service;

import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.order.application.dto.OrderSummaryView;
import com.choco.order.application.dto.OrderView;
import com.choco.order.domain.order.Order;
import com.choco.order.domain.order.OrderStatus;
import com.choco.order.domain.port.OrderRepository;
import java.util.Collection;
import java.util.EnumSet;
import java.util.UUID;

/**
 * The read side of orders.
 *
 * <p>Ownership is part of every query rather than a check after the fact. {@link #ownOrder} loads the
 * order and then asserts it belongs to the caller, and {@link #ownHistory} filters by customer id in
 * the query itself. A customer therefore cannot reach another's order by guessing an id, and there is
 * no code path where the check could be forgotten because there is no method that returns an order
 * without it.
 *
 * <p>The seller's views are separate methods with their own names, so it is obvious at a call site
 * which audience a query serves.
 */
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** One of the caller's own orders. */
    public OrderView ownOrder(UUID customerId, UUID orderId) {
        Order order = require(orderId);
        order.assertOwnedBy(customerId);
        return OrderViewMapper.toView(order);
    }

    /** A page of the caller's own orders, newest first. */
    public PageResult<OrderSummaryView> ownHistory(UUID customerId, PageQuery page) {
        return orderRepository.findByCustomer(customerId, page).map(OrderViewMapper::toSummary);
    }

    /** Any order, for the seller. */
    public OrderView forAdmin(UUID orderId) {
        return OrderViewMapper.toView(require(orderId));
    }

    /**
     * A page of orders for the seller's dashboard, optionally narrowed by status, customer and a search
     * term matched against the order number.
     */
    public PageResult<OrderSummaryView> searchForAdmin(Collection<OrderStatus> statuses, UUID customerId,
                                                       String search, PageQuery page) {
        Collection<OrderStatus> effective = (statuses == null || statuses.isEmpty())
                ? EnumSet.allOf(OrderStatus.class)
                : statuses;
        String normalised = search == null || search.isBlank() ? null : search.trim();
        return orderRepository.findByStatuses(effective, customerId, normalised, page)
                .map(OrderViewMapper::toSummary);
    }

    /** The seller's workload counters, in one place so the dashboard and reports agree. */
    public OrderCounters counters() {
        return new OrderCounters(
                orderRepository.count(),
                orderRepository.countByStatus(OrderStatus.PAYMENT_PENDING),
                orderRepository.countByStatus(OrderStatus.CONFIRMED),
                orderRepository.countByStatus(OrderStatus.PREPARATION_STARTED),
                orderRepository.countByStatus(OrderStatus.PREPARATION_ON_HOLD),
                orderRepository.countByStatus(OrderStatus.PREPARATION_COMPLETED),
                orderRepository.countByStatus(OrderStatus.PACKAGING),
                orderRepository.countByStatus(OrderStatus.READY_FOR_DISPATCH),
                orderRepository.countByStatus(OrderStatus.OUT_FOR_DELIVERY),
                orderRepository.countByStatus(OrderStatus.DELIVERED),
                orderRepository.countByStatus(OrderStatus.PAYMENT_FAILED),
                orderRepository.countByStatus(OrderStatus.CANCELLED),
                orderRepository.countByStatus(OrderStatus.REFUND_UNDER_REVIEW));
    }

    private Order require(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Order not found"));
    }

    /** Counts by lifecycle stage, used by the admin dashboard and the order reports. */
    public record OrderCounters(
            long total,
            long paymentPending,
            long awaitingPreparation,
            long inPreparation,
            long preparationOnHold,
            long preparationCompleted,
            long packaging,
            long readyForDispatch,
            long outForDelivery,
            long delivered,
            long paymentFailed,
            long cancelled,
            long refundUnderReview) {
    }
}
