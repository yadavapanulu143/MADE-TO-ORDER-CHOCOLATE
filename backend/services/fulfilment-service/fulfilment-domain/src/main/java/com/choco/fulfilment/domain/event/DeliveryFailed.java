package com.choco.fulfilment.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Raised when a delivery attempt does not succeed. */
public record DeliveryFailed(
        UUID eventId,
        Instant occurredAt,
        String deliveryId,
        String orderId,
        String orderNumber,
        String customerId,
        String status,
        String failureReason,
        int attemptNumber) implements DomainEvent {

    @Override
    public String eventType() {
        return "fulfilment.delivery-failed";
    }

    @Override
    public String aggregateId() {
        return orderId;
    }
}
