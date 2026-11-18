package com.choco.fulfilment.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Raised when the seller schedules another delivery attempt after one failed. */
public record DeliveryRescheduled(
        UUID eventId,
        Instant occurredAt,
        String deliveryId,
        String orderId,
        String orderNumber,
        String customerId,
        String status,
        int attemptNumber) implements DomainEvent {

    @Override
    public String eventType() {
        return "fulfilment.delivery-rescheduled";
    }

    @Override
    public String aggregateId() {
        return orderId;
    }
}
