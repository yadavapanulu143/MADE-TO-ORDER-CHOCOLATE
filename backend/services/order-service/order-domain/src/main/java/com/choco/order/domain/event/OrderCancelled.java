package com.choco.order.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when an order is cancelled, by the customer or the seller.
 *
 * <p>The reason is carried so the notification and the audit trail explain the cancellation without a
 * second lookup.
 */
public record OrderCancelled(
        UUID eventId,
        Instant occurredAt,
        String orderId,
        String customerId,
        String orderNumber,
        String reason,
        String cancelledBy) implements DomainEvent {

    @Override
    public String eventType() {
        return "order.cancelled";
    }

    @Override
    public String aggregateId() {
        return orderId;
    }
}
