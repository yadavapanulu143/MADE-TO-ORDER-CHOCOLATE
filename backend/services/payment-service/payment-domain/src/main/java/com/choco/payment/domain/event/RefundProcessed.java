package com.choco.payment.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Money was returned to the customer.
 *
 * <p>Emitted only after the provider confirms the refund, never when the seller merely decides to
 * refund. The order service moves the order to refunded on this event, so a decision the provider
 * later rejects cannot mark an order as refunded.
 */
public record RefundProcessed(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        String paymentId,
        String orderId,
        String providerRefundId,
        long amountMinorUnits,
        String currency,
        boolean fullRefund) implements DomainEvent {

    @Override
    public String eventType() {
        return "payment.refund-processed";
    }
}
