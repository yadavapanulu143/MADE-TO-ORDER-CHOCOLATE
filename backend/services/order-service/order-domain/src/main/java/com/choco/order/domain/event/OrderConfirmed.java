package com.choco.order.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a verified payment moves the order into preparation.
 *
 * <p>This is the event the made-to-order process waits for: preparation must not begin until a
 * payment has been verified, so the fulfilment service keys off this and never off {@code order.placed}.
 */
public record OrderConfirmed(
        UUID eventId,
        Instant occurredAt,
        String orderId,
        String customerId,
        String orderNumber,
        String paymentId) implements DomainEvent {

    @Override
    public String eventType() {
        return "order.confirmed";
    }

    @Override
    public String aggregateId() {
        return orderId;
    }
}
