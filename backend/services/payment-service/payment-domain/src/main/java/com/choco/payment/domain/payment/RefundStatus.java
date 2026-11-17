package com.choco.payment.domain.payment;

/**
 * Lifecycle of a refund against a payment.
 *
 * <p>Requested is separate from processed on purpose: the seller deciding to refund and the provider
 * confirming the money moved are different facts, and an order must only be marked refunded on the
 * second.
 */
public enum RefundStatus {
    REQUESTED,
    PROCESSING,
    PROCESSED,
    FAILED;

    public boolean isTerminal() {
        return this == PROCESSED || this == FAILED;
    }
}
