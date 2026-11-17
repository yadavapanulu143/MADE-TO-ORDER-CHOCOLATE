package com.choco.payment.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * A payment attempt failed, or could not be verified.
 *
 * <p>Carries the reason so the notification service can tell the customer something useful and the
 * order service can move the order to a payment-failed state the customer can retry from.
 */
public record PaymentFailed(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        String orderId,
        String customerId,
        String providerPaymentId,
        long amountMinorUnits,
        String currency,
        String reason) implements DomainEvent {

    @Override
    public String eventType() {
        return "payment.failed";
    }
}
