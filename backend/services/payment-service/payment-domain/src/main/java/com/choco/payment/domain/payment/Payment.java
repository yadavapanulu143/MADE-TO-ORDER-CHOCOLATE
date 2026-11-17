package com.choco.payment.domain.payment;

import com.choco.core.domain.AggregateRoot;
import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.payment.domain.event.DisputeRaised;
import com.choco.payment.domain.event.PaymentCaptured;
import com.choco.payment.domain.event.PaymentFailed;
import com.choco.payment.domain.event.PaymentPending;
import com.choco.payment.domain.event.RefundProcessed;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A payment for one order: the provider order created against it, every attempt, every refund and any
 * dispute.
 *
 * <p>One aggregate per order, not per attempt. A customer may retry against the same provider order,
 * and a refund always belongs to the payment it reverses, so the payment is the natural consistency
 * boundary and the only way to guarantee that a second capture cannot arrive after the money was
 * already returned.
 *
 * <p><b>The central rule.</b> A payment becomes {@code PAID} only through {@link #markCaptured},
 * which is called from a server-side verification or a signature-checked webhook — never from anything
 * the browser says. A redirect back to the storefront proves nothing: a customer can reach the success
 * URL without paying, and a malicious one can forge it. The capture method also checks the amount the
 * provider reports against the amount that was charged, so a smaller capture cannot be recorded as a
 * full payment.
 *
 * <p><b>Idempotency.</b> Providers deliver webhooks at least once, and a retry storm around a capture
 * is normal. {@link #markCaptured} is therefore idempotent for the same provider payment id, and
 * refuses a <em>different</em> payment id against an already-paid order, which is the signature of a
 * double charge rather than a duplicate delivery.
 */
public class Payment extends AggregateRoot {

    public static final int MAX_REASON_LENGTH = 500;

    private final UUID id;
    private final UUID orderId;
    private final UUID customerId;
    private final Money amount;
    private final String receipt;
    private final String provider;
    private final String providerOrderId;
    private final Instant createdAt;

    private PaymentStatus status;
    private String providerPaymentId;
    private PaymentMethod method;
    private String failureReason;
    private Instant updatedAt;

    private final List<PaymentAttempt> attempts = new ArrayList<>();
    private final List<Refund> refunds = new ArrayList<>();
    private final List<Dispute> disputes = new ArrayList<>();

    private Payment(UUID id, UUID orderId, UUID customerId, Money amount, String receipt, String provider,
                    String providerOrderId, PaymentStatus status, Instant createdAt) {
        if (amount == null || amount.isZero() || amount.isNegative()) {
            throw new IllegalArgumentException("A payment amount must be greater than zero");
        }
        this.id = id;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.receipt = receipt;
        this.provider = provider;
        this.providerOrderId = providerOrderId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * Opens a payment for an order, with the provider order already created.
     *
     * <p>Starts in {@code CREATED}: the provider order exists but nothing has been paid. No event is
     * raised, because "a payment could be attempted" is not yet a fact the rest of the platform cares
     * about.
     */
    public static Payment open(UUID id, UUID orderId, UUID customerId, Money amount, String receipt,
                               String provider, String providerOrderId, Instant now) {
        return new Payment(id, orderId, customerId, amount, receipt, provider, providerOrderId,
                PaymentStatus.CREATED, now);
    }

    /** Rehydrates from persistence without re-running creation rules or re-raising events. */
    public static Payment rehydrate(UUID id, UUID orderId, UUID customerId, Money amount, String receipt,
                                    String provider, String providerOrderId, PaymentStatus status,
                                    String providerPaymentId, PaymentMethod method, String failureReason,
                                    Instant createdAt, Instant updatedAt) {
        Payment payment = new Payment(id, orderId, customerId, amount, receipt, provider, providerOrderId,
                status, createdAt);
        payment.providerPaymentId = providerPaymentId;
        payment.method = method;
        payment.failureReason = failureReason;
        payment.updatedAt = updatedAt;
        return payment;
    }

    /** Reattaches persisted children after the root has been loaded. Called only by the mapper. */
    public void attachAttempts(List<PaymentAttempt> loaded) {
        attempts.clear();
        attempts.addAll(loaded);
    }

    public void attachRefunds(List<Refund> loaded) {
        refunds.clear();
        refunds.addAll(loaded);
    }

    public void attachDisputes(List<Dispute> loaded) {
        disputes.clear();
        disputes.addAll(loaded);
    }

    // -------------------------------------------------------------------------------------------
    // Verification-driven transitions (never from a browser)
    // -------------------------------------------------------------------------------------------

    /**
     * Records that an attempt is in flight.
     *
     * <p>Used when the storefront has opened the provider's checkout and the provider reports an
     * authorised-but-not-captured payment. The order stays unpaid: an authorisation is a promise, not
     * money, and a UPI mandate that is never captured must not confirm an order.
     */
    public void markPending(String providerPaymentId, PaymentMethod method, Instant now) {
        requireTransition(PaymentStatus.PENDING);
        this.providerPaymentId = providerPaymentId;
        if (method != null) {
            this.method = method;
        }
        this.status = PaymentStatus.PENDING;
        this.updatedAt = now;
        recordAttempt(providerPaymentId, method, PaymentStatus.PENDING, null, now);
        registerEvent(new PaymentPending(UUID.randomUUID(), now, id.toString(), orderId.toString(),
                customerId.toString(), providerPaymentId, amount.minorUnits(),
                amount.currency().getCurrencyCode()));
    }

    /**
     * Records a verified capture. The only path to {@code PAID}.
     *
     * <p>Idempotent for a repeated delivery of the same payment: a webhook that arrives twice, or a
     * webhook and a server-side verification racing, must not raise two captured events. A capture for
     * a <em>different</em> provider payment id against an already-paid payment is refused as a double
     * charge, which is a real problem the seller has to refund rather than one to silently absorb.
     *
     * @param capturedAmount the amount the provider reports as captured, checked against the charged
     *                       amount so a partial or tampered capture is never recorded as full payment
     */
    public void markCaptured(String providerPaymentId, PaymentMethod method, Money capturedAmount,
                             Instant now) {
        if (status == PaymentStatus.PAID) {
            if (providerPaymentId != null && providerPaymentId.equals(this.providerPaymentId)) {
                return;
            }
            throw new BusinessRuleException(ErrorCode.PAYMENT_ALREADY_CAPTURED,
                    "This order has already been paid");
        }
        if (status == PaymentStatus.REFUNDED || status == PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_ALREADY_CAPTURED,
                    "Money has already been returned for this order");
        }
        if (capturedAmount != null && !capturedAmount.equals(amount)) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_VERIFICATION_FAILED,
                    "The captured amount does not match the amount that was charged");
        }
        requireTransition(PaymentStatus.PAID);

        this.providerPaymentId = providerPaymentId;
        if (method != null) {
            this.method = method;
        }
        this.status = PaymentStatus.PAID;
        this.failureReason = null;
        this.updatedAt = now;
        recordAttempt(providerPaymentId, method, PaymentStatus.PAID, null, now);
        registerEvent(new PaymentCaptured(UUID.randomUUID(), now, id.toString(), orderId.toString(),
                customerId.toString(), providerPaymentId, providerOrderId, amount.minorUnits(),
                amount.currency().getCurrencyCode(), this.method == null ? null : this.method.name()));
    }

    /** Records a failed or unverifiable attempt. A later retry is allowed against the same payment. */
    public void markFailed(String providerPaymentId, PaymentMethod method, String reason, Instant now) {
        if (status == PaymentStatus.PAID) {
            // A capture already succeeded; a later "failed" delivery is stale and must not undo it.
            return;
        }
        if (status == PaymentStatus.REFUNDED) {
            return;
        }
        requireTransition(PaymentStatus.FAILED);
        this.providerPaymentId = providerPaymentId;
        if (method != null) {
            this.method = method;
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = normaliseReason(reason);
        this.updatedAt = now;
        recordAttempt(providerPaymentId, method, PaymentStatus.FAILED, reason, now);
        registerEvent(new PaymentFailed(UUID.randomUUID(), now, id.toString(), orderId.toString(),
                customerId.toString(), providerPaymentId, amount.minorUnits(),
                amount.currency().getCurrencyCode(), this.failureReason));
    }

    // -------------------------------------------------------------------------------------------
    // Refunds
    // -------------------------------------------------------------------------------------------

    /**
     * Requests a refund of part or all of the settled amount.
     *
     * <p>Refused unless the money is actually with the seller: refunding an unpaid order would be
     * paying out money that never arrived. The total of all refunds may never exceed the captured
     * amount, which is the check that stops two "full" refunds being issued for one payment.
     */
    public Refund requestRefund(UUID refundId, Money refundAmount, String reason, String requestedBy,
                                Instant now) {
        if (!status.isSettledWithSeller()) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Only a paid payment can be refunded");
        }
        if (refundAmount == null || refundAmount.isZero() || refundAmount.isNegative()) {
            throw new IllegalArgumentException("A refund amount must be greater than zero");
        }
        Money alreadyRefunded = refundedAmount();
        Money remaining = amount.minus(alreadyRefunded);
        if (refundAmount.isGreaterThan(remaining)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "A refund may not exceed the amount still held for this payment");
        }
        Refund refund = Refund.request(refundId, id, refundAmount, reason, requestedBy, now);
        refunds.add(refund);
        this.updatedAt = now;
        return refund;
    }

    /** Marks a refund in flight with the provider. */
    public void markRefundProcessing(UUID refundId, String providerRefundId, Instant now) {
        Refund refund = requireRefund(refundId);
        refund.markProcessing(providerRefundId);
        this.updatedAt = now;
    }

    /**
     * Records the provider's confirmation that a refund settled, and moves the payment to refunded or
     * partially refunded.
     *
     * <p>Idempotent: a repeated confirmation for an already-processed refund changes nothing and
     * raises no second event.
     */
    public void markRefundProcessed(UUID refundId, String providerRefundId, Instant now) {
        Refund refund = requireRefund(refundId);
        if (refund.status() == RefundStatus.PROCESSED) {
            return;
        }
        refund.markProcessed(providerRefundId, now);

        Money total = refundedAmount();
        PaymentStatus next = total.compareTo(amount) >= 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED;
        if (status.canTransitionTo(next)) {
            this.status = next;
        }
        this.updatedAt = now;
        registerEvent(new RefundProcessed(UUID.randomUUID(), now, id.toString(), id.toString(),
                orderId.toString(), refund.providerRefundId(), refund.amount().minorUnits(),
                refund.amount().currency().getCurrencyCode(), next == PaymentStatus.REFUNDED));
    }

    /** Records that the provider rejected a refund request. */
    public void markRefundFailed(UUID refundId, String reason, Instant now) {
        Refund refund = requireRefund(refundId);
        refund.markFailed(reason);
        this.updatedAt = now;
    }

    // -------------------------------------------------------------------------------------------
    // Disputes
    // -------------------------------------------------------------------------------------------

    /** Records a dispute the provider raised, and stops treating the money as safely settled. */
    public void raiseDispute(UUID disputeId, String providerDisputeId, Money disputeAmount, String reason,
                             Instant now) {
        if (status != PaymentStatus.PAID && status != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "A dispute can only be raised against a paid payment");
        }
        Dispute dispute = Dispute.open(disputeId, id, providerDisputeId, disputeAmount, reason, now);
        disputes.add(dispute);
        this.status = PaymentStatus.DISPUTED;
        this.updatedAt = now;
        registerEvent(new DisputeRaised(UUID.randomUUID(), now, id.toString(), id.toString(),
                orderId.toString(), providerDisputeId, disputeAmount.minorUnits(),
                disputeAmount.currency().getCurrencyCode(), reason));
    }

    // -------------------------------------------------------------------------------------------
    // Derived reads
    // -------------------------------------------------------------------------------------------

    /** The sum of every refund that has actually been processed. */
    public Money refundedAmount() {
        return refunds.stream()
                .filter(r -> r.status() == RefundStatus.PROCESSED)
                .map(Refund::amount)
                .reduce(Money.inr(0), Money::plus);
    }

    public boolean isPaid() {
        return status == PaymentStatus.PAID || status == PaymentStatus.PARTIALLY_REFUNDED
                || status == PaymentStatus.DISPUTED;
    }

    public Optional<Refund> refund(UUID refundId) {
        return refunds.stream().filter(r -> r.id().equals(refundId)).findFirst();
    }

    public List<PaymentAttempt> attempts() {
        return attempts.stream().sorted(Comparator.comparing(PaymentAttempt::attemptedAt)).toList();
    }

    public List<Refund> refunds() {
        return List.copyOf(refunds);
    }

    public List<Dispute> disputes() {
        return List.copyOf(disputes);
    }

    // -------------------------------------------------------------------------------------------

    private void recordAttempt(String providerPaymentId, PaymentMethod method, PaymentStatus outcome,
                               String failureReason, Instant now) {
        attempts.add(PaymentAttempt.of(UUID.randomUUID(), providerPaymentId, amount, method, outcome,
                normaliseReason(failureReason), now));
    }

    private Refund requireRefund(UUID refundId) {
        return refund(refundId).orElseThrow(() -> new BusinessRuleException(ErrorCode.NOT_FOUND,
                "Refund not found on this payment"));
    }

    private void requireTransition(PaymentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "A payment cannot move from " + status + " to " + next);
        }
    }

    private static String normaliseReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.length() <= MAX_REASON_LENGTH ? trimmed : trimmed.substring(0, MAX_REASON_LENGTH);
    }

    public UUID id() {
        return id;
    }

    public UUID orderId() {
        return orderId;
    }

    public UUID customerId() {
        return customerId;
    }

    public Money amount() {
        return amount;
    }

    public String receipt() {
        return receipt;
    }

    public String provider() {
        return provider;
    }

    public String providerOrderId() {
        return providerOrderId;
    }

    public PaymentStatus status() {
        return status;
    }

    public String providerPaymentId() {
        return providerPaymentId;
    }

    public PaymentMethod method() {
        return method;
    }

    public String failureReason() {
        return failureReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
