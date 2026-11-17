package com.choco.fulfilment.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Raised when the seller records that a parcel has left for the customer. */
public record OutForDelivery(
        UUID eventId,
        Instant occurredAt,
        String deliveryId,
        String orderId,
        String orderNumber,
        String customerId,
        String status,
        String deliveryPersonName) implements DomainEvent {

    @Override
    public String eventType() {
        return "fulfilment.out-for-delivery";
    }

    @Override
    public String aggregateId() {
        return orderId;
    }
}
