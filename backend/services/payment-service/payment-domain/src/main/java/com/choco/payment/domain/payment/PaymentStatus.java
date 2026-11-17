package com.choco.payment.domain.payment;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a payment, from the provider order being created to money either settled, lost or
 * returned.
 *
 * <p>The states are deliberately few and each means one thing:
 * <ul>
 *   <li>{@code CREATED} — the provider order exists but no payment has been attempted yet.</li>
 *   <li>{@code PENDING} — a payment is in flight or awaiting server-side verification. This is the
 *       honest state while a UPI mandate or net-banking redirect is unresolved: the customer may
 *       have paid, but nothing has been confirmed, so the order must not be treated as paid.</li>
 *   <li>{@code PAID} — a verified capture. Only a server-side verification or a signature-checked
 *       webhook can reach this state; never a browser redirect.</li>
 *   <li>{@code FAILED} — the attempt failed or could not be verified.</li>
 *   <li>{@code PARTIALLY_REFUNDED} / {@code REFUNDED} — money was returned, wholly or in part.</li>
 *   <li>{@code DISPUTED} — the provider opened a dispute or chargeback.</li>
 * </ul>
 *
 * <p>A payment that is {@code PAID} may still move to a refunded or disputed state, so "paid" is not
 * terminal. {@code REFUNDED} is terminal: once the money is back with the customer there is nothing
 * further this service does with the payment.
 */
public enum PaymentStatus {

    CREATED,
    PENDING,
    PAID,
    FAILED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    DISPUTED;

    /**
     * Which states each state may move to.
     *
     * <p>Declared as a table rather than a chain of {@code if}s so a reviewer can see the whole
     * machine at once and a test can walk every pair.
     */
    public Set<PaymentStatus> allowedTransitions() {
        return switch (this) {
            case CREATED -> EnumSet.of(PENDING, PAID, FAILED);
            // A pending attempt may settle, fail, or be confirmed later by a webhook.
            case PENDING -> EnumSet.of(PAID, FAILED);
            // Paid money can be returned or disputed; it cannot silently go back to pending.
            case PAID -> EnumSet.of(PARTIALLY_REFUNDED, REFUNDED, DISPUTED);
            // A failed attempt may be retried, which creates a fresh pending state on the same
            // provider order.
            case FAILED -> EnumSet.of(PENDING, PAID, FAILED);
            case PARTIALLY_REFUNDED -> EnumSet.of(REFUNDED, DISPUTED);
            case REFUNDED -> EnumSet.noneOf(PaymentStatus.class);
            case DISPUTED -> EnumSet.of(PARTIALLY_REFUNDED, REFUNDED);
        };
    }

    public boolean canTransitionTo(PaymentStatus next) {
        return this == next || allowedTransitions().contains(next);
    }

    /** Whether the customer's money is currently with the seller. */
    public boolean isSettledWithSeller() {
        return this == PAID || this == PARTIALLY_REFUNDED || this == DISPUTED;
    }

    public boolean isTerminal() {
        return this == REFUNDED;
    }
}
