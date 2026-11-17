package com.choco.fulfilment.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Raised when a delivery is confirmed received by the customer. */
public record DeliveryDelivered(
        UUID eventId,
        Instant occurredAt,
        String deliveryId,
        String orderId,
        String orderNumber,
        String customerId,
        String status,
        String proofReference) implements DomainEvent {

    @Override
    public String eventType() {
        return "fulfilment.delivery-delivered";
    }

    @Override
    public String aggregateId() {
        return orderId;
    }
}
