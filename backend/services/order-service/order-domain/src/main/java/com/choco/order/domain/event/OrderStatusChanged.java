package com.choco.order.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised on every lifecycle transition, so consumers can follow an order without polling it.
 *
 * <p>Both the previous and the new status are carried, because a consumer that only saw the new value
 * could not tell a cancellation from a delivery without keeping its own history.
 */
public record OrderStatusChanged(
        UUID eventId,
        Instant occurredAt,
        String orderId,
        String customerId,
        String previousStatus,
        String newStatus,
        String reason) implements DomainEvent {

    @Override
    public String eventType() {
        return "order.status-changed";
    }

    @Override
    public String aggregateId() {
        return orderId;
    }
}
