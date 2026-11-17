package com.choco.payment.domain.payment;

import com.choco.core.domain.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * A dispute or chargeback the provider raised against a payment.
 *
 * <p>Held as a record of a fact rather than a workflow this service drives: the provider owns the
 * process and reports its outcome. The service's job is to keep the seller informed and to stop
 * treating the money as safely settled while a dispute is open.
 */
public record Dispute(
        UUID id,
        UUID paymentId,
        String providerDisputeId,
        Money amount,
        String reason,
        String status,
        Instant raisedAt,
        Instant resolvedAt) {

    public static Dispute open(UUID id, UUID paymentId, String providerDisputeId, Money amount,
                               String reason, Instant now) {
        return new Dispute(id, paymentId, providerDisputeId, amount, reason, "OPEN", now, null);
    }

    public boolean isOpen() {
        return resolvedAt == null;
    }
}
