package com.choco.payment.application.service;

import com.choco.core.domain.Money;
import com.choco.payment.application.dto.AttemptView;
import com.choco.payment.application.dto.PaymentSummaryView;
import com.choco.payment.application.dto.PaymentView;
import com.choco.payment.application.dto.RefundView;
import com.choco.payment.domain.payment.Dispute;
import com.choco.payment.domain.payment.Payment;
import com.choco.payment.domain.payment.PaymentAttempt;
import com.choco.payment.domain.payment.Refund;
import java.math.RoundingMode;

/**
 * Maps a payment aggregate onto the views the API returns.
 *
 * <p>A hand-written mapper rather than reflection or a bean copier: a field added to the aggregate
 * should force a decision about whether it belongs in a response, and reflection would happily export
 * it whether it was safe to or not.
 */
final class PaymentViewMapper {

    private PaymentViewMapper() {
    }

    static PaymentView toView(Payment payment) {
        return new PaymentView(
                payment.id(),
                payment.orderId(),
                payment.customerId(),
                payment.amount().minorUnits(),
                decimal(payment.amount()),
                payment.amount().currency().getCurrencyCode(),
                payment.status().name(),
                payment.provider(),
                payment.providerOrderId(),
                payment.providerPaymentId(),
                payment.method() == null ? null : payment.method().name(),
                payment.failureReason(),
                payment.refundedAmount().minorUnits(),
                decimal(payment.refundedAmount()),
                payment.createdAt(),
                payment.updatedAt(),
                payment.refunds().stream().map(PaymentViewMapper::toRefundView).toList(),
                payment.attempts().stream().map(PaymentViewMapper::toAttemptView).toList());
    }

    static PaymentSummaryView toSummary(Payment payment) {
        return new PaymentSummaryView(
                payment.id(),
                payment.orderId(),
                payment.amount().minorUnits(),
                decimal(payment.amount()),
                payment.amount().currency().getCurrencyCode(),
                payment.status().name(),
                payment.method() == null ? null : payment.method().name(),
                payment.createdAt());
    }

    static RefundView toRefundView(Refund refund) {
        return new RefundView(
                refund.id(),
                refund.amount().minorUnits(),
                decimal(refund.amount()),
                refund.status().name(),
                refund.reason(),
                refund.providerRefundId(),
                refund.failureReason(),
                refund.requestedAt(),
                refund.processedAt());
    }

    static AttemptView toAttemptView(PaymentAttempt attempt) {
        return new AttemptView(
                attempt.id(),
                attempt.providerPaymentId(),
                attempt.method() == null ? null : attempt.method().name(),
                attempt.outcome().name(),
                attempt.failureReason(),
                attempt.attemptedAt());
    }

    static String decimal(Money money) {
        return money.toMajorUnits().setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
