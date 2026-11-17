package com.choco.payment.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * A payment is in flight and not yet confirmed.
 *
 * <p>Emitted when an attempt is created or a webhook reports an authorised-but-not-captured payment.
 * The order stays unpaid until a capture is verified, so this event is informational: it lets the
 * storefront show "payment processing" rather than "failed".
 */
public record PaymentPending(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        String orderId,
        String customerId,
        String providerPaymentId,
        long amountMinorUnits,
        String currency) implements DomainEvent {

    @Override
    public String eventType() {
        return "payment.pending";
    }
}
