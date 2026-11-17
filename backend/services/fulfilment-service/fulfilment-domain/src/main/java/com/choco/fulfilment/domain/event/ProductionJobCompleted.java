package com.choco.fulfilment.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when the chocolate is made and preparation is complete.
 *
 * <p>Distinct from {@code ready-for-dispatch}: the customer is told preparation finished here, and the
 * "ready" event is raised later once the product is packed.
 */
public record ProductionJobCompleted(
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
        return "fulfilment.production-completed";
    }

    @Override
    public String aggregateId() {
        return orderId;
    }
}
