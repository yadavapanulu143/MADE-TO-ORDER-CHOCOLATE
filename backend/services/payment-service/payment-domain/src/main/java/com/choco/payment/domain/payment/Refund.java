package com.choco.payment.domain.payment;

import com.choco.core.domain.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * A refund against a payment.
 *
 * <p>Created when the seller decides to return money, and moved to {@code PROCESSED} only when the
 * provider confirms it. The amount is always a positive value in the customer's favour; the sign is
 * carried by the fact that this is a refund, not by a negative number, which is easier to reason about
 * and impossible to accidentally add to a total.
 */
public final class Refund {

    private final UUID id;
    private final UUID paymentId;
    private final Money amount;
    private final String reason;
    private final String requestedBy;
    private final Instant requestedAt;
    private RefundStatus status;
    private String providerRefundId;
    private String failureReason;
    private Instant processedAt;

    private Refund(UUID id, UUID paymentId, Money amount, String reason, String requestedBy,
                   Instant requestedAt) {
        if (amount == null || amount.isZero() || amount.isNegative()) {
            throw new IllegalArgumentException("A refund amount must be greater than zero");
        }
        this.id = id;
        this.paymentId = paymentId;
        this.amount = amount;
        this.reason = normaliseReason(reason);
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
        this.status = RefundStatus.REQUESTED;
    }

    public static Refund request(UUID id, UUID paymentId, Money amount, String reason, String requestedBy,
                                 Instant now) {
        return new Refund(id, paymentId, amount, reason, requestedBy, now);
    }

    /** Rehydrates from persistence without re-running creation rules. */
    public static Refund rehydrate(UUID id, UUID paymentId, Money amount, String reason, String requestedBy,
                                   Instant requestedAt, RefundStatus status, String providerRefundId,
                                   String failureReason, Instant processedAt) {
        Refund refund = new Refund(id, paymentId, amount, reason, requestedBy, requestedAt);
        refund.status = status;
        refund.providerRefundId = providerRefundId;
        refund.failureReason = failureReason;
        refund.processedAt = processedAt;
        return refund;
    }

    /** Marks the refund in flight with the provider. */
    public void markProcessing(String providerRefundId) {
        requireStatus(RefundStatus.REQUESTED, "start processing");
        this.providerRefundId = providerRefundId;
        this.status = RefundStatus.PROCESSING;
    }

    /** Records the provider's confirmation that the money has moved. */
    public void markProcessed(String providerRefundId, Instant now) {
        if (status == RefundStatus.PROCESSED) {
            return;
        }
        if (status == RefundStatus.FAILED) {
            throw new IllegalStateException("A failed refund cannot be marked processed");
        }
        if (providerRefundId != null && !providerRefundId.isBlank()) {
            this.providerRefundId = providerRefundId;
        }
        this.status = RefundStatus.PROCESSED;
        this.processedAt = now;
    }

    /** Records that the provider rejected the refund. */
    public void markFailed(String reason) {
        if (status == RefundStatus.PROCESSED) {
            throw new IllegalStateException("A processed refund cannot be marked failed");
        }
        this.status = RefundStatus.FAILED;
        this.failureReason = normaliseReason(reason);
    }

    private void requireStatus(RefundStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException("Cannot " + action + " a refund in state " + status);
        }
    }

    private static String normaliseReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    public UUID id() {
        return id;
    }

    public UUID paymentId() {
        return paymentId;
    }

    public Money amount() {
        return amount;
    }

    public String reason() {
        return reason;
    }

    public String requestedBy() {
        return requestedBy;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public RefundStatus status() {
        return status;
    }

    public String providerRefundId() {
        return providerRefundId;
    }

    public String failureReason() {
        return failureReason;
    }

    public Instant processedAt() {
        return processedAt;
    }
}
