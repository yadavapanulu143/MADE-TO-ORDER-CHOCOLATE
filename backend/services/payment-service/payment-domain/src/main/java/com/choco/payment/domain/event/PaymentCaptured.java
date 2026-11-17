package com.choco.payment.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * A payment was verified as captured.
 *
 * <p>Carries the order id so the order service can move its order to a paid state, and the amount so
 * the consumer can cross-check that what was captured matches what was charged. It carries no card or
 * UPI detail: the provider never gives this service anything sensitive to forward.
 */
public record PaymentCaptured(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        String orderId,
        String customerId,
        String providerPaymentId,
        String providerOrderId,
        long amountMinorUnits,
        String currency,
        String method) implements DomainEvent {

    @Override
    public String eventType() {
        return "payment.captured";
    }
}
