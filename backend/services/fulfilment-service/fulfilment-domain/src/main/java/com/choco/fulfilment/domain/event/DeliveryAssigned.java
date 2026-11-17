package com.choco.fulfilment.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when the seller first records who will deliver an order.
 *
 * <p>Only the first assignment is news. The delivery person is an employee of the seller and the
 * customer never contacts them, so a later re-assignment — the first person becoming unavailable — is
 * a change to the seller's own roster, not a fact worth telling the customer about. The event is
 * therefore raised on the {@code PENDING → ASSIGNED} edge only, and a re-assignment while the parcel
 * is still with the seller raises nothing.
 */
public record DeliveryAssigned(
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
        return "fulfilment.delivery-assigned";
    }

    @Override
    public String aggregateId() {
        return orderId;
    }
}
