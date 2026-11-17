package com.choco.order.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a customer asks for a refund.
 *
 * <p>The event is a request, not a decision. The seller reviews it — the business is made-to-order, so
 * a cancellation after preparation has begun is not costless, but neither is it automatically
 * refused — and the outcome is a separate {@code payment.refund-processed} event from the payment
 * service.
 */
public record RefundRequested(
        UUID eventId,
        Instant occurredAt,
        String orderId,
        String customerId,
        String orderNumber,
        long amountMinorUnits,
        String currency,
        String reason) implements DomainEvent {

    @Override
    public String eventType() {
        return "order.refund-requested";
    }

    @Override
    public String aggregateId() {
        return orderId;
    }
}
