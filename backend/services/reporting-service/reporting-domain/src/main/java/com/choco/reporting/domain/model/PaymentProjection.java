package com.choco.reporting.domain.model;

import java.time.Instant;

/**
 * The reporting view of a payment, projected from the payment service's events.
 *
 * <p>The status is the payment service's own vocabulary — {@code PAID}, {@code FAILED},
 * {@code PENDING} — derived from which event arrived. Only a {@code PAID} payment is money received, so
 * only a paid payment is summed into revenue; a pending or failed one is counted but not added. The
 * event type is what decides it, because a payment event carries no status field of its own; the report
 * and the customer's screen therefore agree.
 */
public record PaymentProjection(
        String paymentId,
        String orderId,
        String customerId,
        String status,
        long amountMinorUnits,
        String currency,
        String method,
        String failureReason,
        Instant occurredAt) {

    public static final String PAID = "PAID";
    public static final String FAILED = "FAILED";
    public static final String PENDING = "PENDING";

    /** Whether this payment is money actually received. */
    public boolean isPaid() {
        return PAID.equals(status);
    }
}
