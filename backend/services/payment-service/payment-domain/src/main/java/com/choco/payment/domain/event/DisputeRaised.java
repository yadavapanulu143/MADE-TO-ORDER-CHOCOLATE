package com.choco.payment.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * The provider opened a dispute or chargeback against a payment.
 *
 * <p>Emitted so the seller is told through the notification and reporting services; a dispute has a
 * deadline to respond, so silence is expensive.
 */
public record DisputeRaised(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        String paymentId,
        String orderId,
        String providerDisputeId,
        long amountMinorUnits,
        String currency,
        String reason) implements DomainEvent {

    @Override
    public String eventType() {
        return "payment.dispute-raised";
    }
}
