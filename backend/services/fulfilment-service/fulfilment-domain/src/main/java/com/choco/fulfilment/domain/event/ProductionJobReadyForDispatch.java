package com.choco.fulfilment.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a job is packed and waiting to be handed to delivery.
 *
 * <p>This is the hand-off point between the production track and the delivery track: the order service
 * moves the order to {@code READY_FOR_DISPATCH} on this event, and the seller then assigns a delivery.
 */
public record ProductionJobReadyForDispatch(
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
        return "fulfilment.ready-for-dispatch";
    }

    @Override
    public String aggregateId() {
        return orderId;
    }
}
