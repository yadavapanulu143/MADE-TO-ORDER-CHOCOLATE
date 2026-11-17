package com.choco.payment.domain.payment;

import com.choco.core.domain.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * One attempt to pay against a provider order.
 *
 * <p>Recorded separately from the payment because a customer may try, fail, and try again against the
 * same provider order — a UPI collect that times out, then a card that succeeds. Keeping every attempt
 * lets the seller see what actually happened when a customer insists they paid, and gives the
 * idempotency check something concrete to compare a repeated webhook against.
 *
 * <p>Only identifiers and a coarse method are held. Nothing here could be used to charge a card.
 */
public record PaymentAttempt(
        UUID id,
        String providerPaymentId,
        Money amount,
        PaymentMethod method,
        PaymentStatus outcome,
        String failureReason,
        Instant attemptedAt) {

    public static PaymentAttempt of(UUID id, String providerPaymentId, Money amount, PaymentMethod method,
                                    PaymentStatus outcome, String failureReason, Instant attemptedAt) {
        return new PaymentAttempt(id, providerPaymentId, amount, method, outcome, failureReason, attemptedAt);
    }
}
