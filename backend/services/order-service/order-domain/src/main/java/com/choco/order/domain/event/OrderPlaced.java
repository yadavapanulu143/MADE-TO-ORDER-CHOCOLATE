package com.choco.order.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a customer places an order, before any payment has been verified.
 *
 * <p>Carries the amount and the line count so a notification consumer can render an order-placed
 * message without calling back into this service. The price is the snapshot taken at checkout, so a
 * later catalogue change cannot alter what the message says the customer agreed to pay.
 */
public record OrderPlaced(
        UUID eventId,
        Instant occurredAt,
        String orderId,
        String customerId,
        String orderNumber,
        long totalMinorUnits,
        String currency,
        int lineCount) implements DomainEvent {

    @Override
    public String eventType() {
        return "order.placed";
    }

    @Override
    public String aggregateId() {
        return orderId;
    }
}
