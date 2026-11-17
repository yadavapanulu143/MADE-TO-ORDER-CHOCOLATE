package com.choco.payment.adapter.out.persistence;

import com.choco.core.domain.Money;
import com.choco.payment.domain.payment.Dispute;
import com.choco.payment.domain.payment.Payment;
import com.choco.payment.domain.payment.PaymentAttempt;
import com.choco.payment.domain.payment.PaymentMethod;
import com.choco.payment.domain.payment.PaymentStatus;
import com.choco.payment.domain.payment.Refund;
import com.choco.payment.domain.payment.RefundStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

/**
 * Explicit mapping between the payment aggregate and its persistence model.
 *
 * <p>Written by hand rather than generated. A reflective mapper would silently follow a field rename
 * or a new field, and the two facts this service most needs to keep straight — the amount charged and
 * the provider identifiers — are exactly the ones a silent mapping error would corrupt. Naming every
 * field makes a change visible in a diff.
 */
final class PaymentPersistenceMapper {

    private PaymentPersistenceMapper() {
    }

    static PaymentJpaEntity toEntity(Payment payment) {
        PaymentJpaEntity entity = new PaymentJpaEntity();
        entity.setId(payment.id());
        entity.setOrderId(payment.orderId());
        entity.setCustomerId(payment.customerId());
        entity.setAmountMinorUnits(payment.amount().minorUnits());
        entity.setCurrency(payment.amount().currency().getCurrencyCode());
        entity.setReceipt(payment.receipt());
        entity.setProvider(payment.provider());
        entity.setProviderOrderId(payment.providerOrderId());
        entity.setProviderPaymentId(payment.providerPaymentId());
        entity.setStatus(payment.status().name());
        entity.setMethod(payment.method() == null ? null : payment.method().name());
        entity.setFailureReason(payment.failureReason());
        entity.setCreatedAt(payment.createdAt());
        entity.setUpdatedAt(payment.updatedAt());

        List<PaymentAttemptJpaEntity> attempts = new ArrayList<>();
        for (PaymentAttempt attempt : payment.attempts()) {
            PaymentAttemptJpaEntity child = new PaymentAttemptJpaEntity();
            child.setId(attempt.id());
            // The owning side must be set before the child joins the collection, or Hibernate writes a
            // null parent key and the not-null foreign key is violated on the first insert.
            child.setPayment(entity);
            child.setProviderPaymentId(attempt.providerPaymentId());
            child.setAmountMinorUnits(attempt.amount().minorUnits());
            child.setMethod(attempt.method() == null ? null : attempt.method().name());
            child.setOutcome(attempt.outcome().name());
            child.setFailureReason(attempt.failureReason());
            child.setAttemptedAt(attempt.attemptedAt());
            attempts.add(child);
        }
        entity.setAttempts(attempts);

        List<RefundJpaEntity> refunds = new ArrayList<>();
        for (Refund refund : payment.refunds()) {
            RefundJpaEntity child = new RefundJpaEntity();
            child.setId(refund.id());
            child.setPayment(entity);
            child.setAmountMinorUnits(refund.amount().minorUnits());
            child.setCurrency(refund.amount().currency().getCurrencyCode());
            child.setStatus(refund.status().name());
            child.setReason(refund.reason());
            child.setRequestedBy(refund.requestedBy());
            child.setProviderRefundId(refund.providerRefundId());
            child.setFailureReason(refund.failureReason());
            child.setRequestedAt(refund.requestedAt());
            child.setProcessedAt(refund.processedAt());
            refunds.add(child);
        }
        entity.setRefunds(refunds);

        List<DisputeJpaEntity> disputes = new ArrayList<>();
        for (Dispute dispute : payment.disputes()) {
            DisputeJpaEntity child = new DisputeJpaEntity();
            child.setId(dispute.id());
            child.setPayment(entity);
            child.setProviderDisputeId(dispute.providerDisputeId());
            child.setAmountMinorUnits(dispute.amount().minorUnits());
            child.setCurrency(dispute.amount().currency().getCurrencyCode());
            child.setReason(dispute.reason());
            child.setStatus(dispute.status());
            child.setRaisedAt(dispute.raisedAt());
            child.setResolvedAt(dispute.resolvedAt());
            disputes.add(child);
        }
        entity.setDisputes(disputes);
        return entity;
    }

    static Payment toDomain(PaymentJpaEntity entity) {
        Payment payment = Payment.rehydrate(
                entity.getId(),
                entity.getOrderId(),
                entity.getCustomerId(),
                Money.ofMinorUnits(entity.getAmountMinorUnits(), Currency.getInstance(entity.getCurrency())),
                entity.getReceipt(),
                entity.getProvider(),
                entity.getProviderOrderId(),
                PaymentStatus.valueOf(entity.getStatus()),
                entity.getProviderPaymentId(),
                entity.getMethod() == null ? null : PaymentMethod.valueOf(entity.getMethod()),
                entity.getFailureReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());

        List<PaymentAttempt> attempts = new ArrayList<>();
        for (PaymentAttemptJpaEntity child : entity.getAttempts()) {
            attempts.add(PaymentAttempt.of(
                    child.getId(),
                    child.getProviderPaymentId(),
                    Money.ofMinorUnits(child.getAmountMinorUnits(),
                            Currency.getInstance(entity.getCurrency())),
                    child.getMethod() == null ? null : PaymentMethod.valueOf(child.getMethod()),
                    PaymentStatus.valueOf(child.getOutcome()),
                    child.getFailureReason(),
                    child.getAttemptedAt()));
        }
        payment.attachAttempts(attempts);

        List<Refund> refunds = new ArrayList<>();
        for (RefundJpaEntity child : entity.getRefunds()) {
            refunds.add(Refund.rehydrate(
                    child.getId(),
                    entity.getId(),
                    Money.ofMinorUnits(child.getAmountMinorUnits(), Currency.getInstance(child.getCurrency())),
                    child.getReason(),
                    child.getRequestedBy(),
                    child.getRequestedAt(),
                    RefundStatus.valueOf(child.getStatus()),
                    child.getProviderRefundId(),
                    child.getFailureReason(),
                    child.getProcessedAt()));
        }
        payment.attachRefunds(refunds);

        List<Dispute> disputes = new ArrayList<>();
        for (DisputeJpaEntity child : entity.getDisputes()) {
            disputes.add(new Dispute(
                    child.getId(),
                    entity.getId(),
                    child.getProviderDisputeId(),
                    Money.ofMinorUnits(child.getAmountMinorUnits(), Currency.getInstance(child.getCurrency())),
                    child.getReason(),
                    child.getStatus(),
                    child.getRaisedAt(),
                    child.getResolvedAt()));
        }
        payment.attachDisputes(disputes);
        return payment;
    }

    static Instant epoch() {
        return Instant.EPOCH;
    }
}
