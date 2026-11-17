package com.choco.fulfilment.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when the seller starts making a customer's chocolate.
 *
 * <p>Carries the order number and the customer id so the notification service can tell the customer
 * without a callback into this service, and the variant label and unit count so the message can be
 * specific rather than a bare order number.
 */
public record ProductionJobStarted(
        UUID eventId,
        Instant occurredAt,
        String jobId,
        String orderId,
        String orderNumber,
        String customerId,
        String variantLabel,
        int quantity,
        String status) implements DomainEvent {

    @Override
    public String eventType() {
        return "fulfilment.production-started";
    }

    @Override
    public String aggregateId() {
        return orderId;
    }
}
