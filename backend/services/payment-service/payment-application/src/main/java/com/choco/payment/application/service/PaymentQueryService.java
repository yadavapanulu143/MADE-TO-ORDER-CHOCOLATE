package com.choco.payment.application.service;

import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.payment.application.dto.PaymentCountersView;
import com.choco.payment.application.dto.PaymentSummaryView;
import com.choco.payment.application.dto.PaymentView;
import com.choco.payment.domain.payment.Payment;
import com.choco.payment.domain.payment.PaymentStatus;
import com.choco.payment.domain.port.PaymentRepository;
import java.util.Collection;
import java.util.EnumSet;
import java.util.UUID;

/**
 * The read side of payments.
 *
 * <p>Ownership is part of the query rather than a check afterwards: {@link #ownPayment} loads a payment
 * scoped to the caller's customer id, so a guessed payment id belonging to someone else is simply not
 * found. There is no method that returns a payment without an audience, so the check cannot be
 * forgotten.
 */
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;

    public PaymentQueryService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /** One of the caller's own payments. */
    public PaymentView ownPayment(UUID customerId, UUID paymentId) {
        return paymentRepository.findByIdAndCustomerId(paymentId, customerId)
                .map(PaymentViewMapper::toView)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Payment not found"));
    }

    /** The payment for one of the caller's own orders. */
    public PaymentView ownPaymentForOrder(UUID customerId, UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .filter(p -> p.customerId().equals(customerId))
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Payment not found"));
        return PaymentViewMapper.toView(payment);
    }

    /** A page of the caller's own payments, newest first. */
    public PageResult<PaymentSummaryView> ownHistory(UUID customerId, PageQuery page) {
        return paymentRepository.findByCustomerId(customerId, page).map(PaymentViewMapper::toSummary);
    }

    /** Any payment, for the seller. */
    public PaymentView forAdmin(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .map(PaymentViewMapper::toView)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Payment not found"));
    }

    /** A page of payments for the seller, optionally narrowed by status and a search term. */
    public PageResult<PaymentSummaryView> searchForAdmin(Collection<PaymentStatus> statuses, String search,
                                                         PageQuery page) {
        Collection<PaymentStatus> effective = (statuses == null || statuses.isEmpty())
                ? EnumSet.allOf(PaymentStatus.class)
                : statuses;
        String normalised = search == null || search.isBlank() ? null : search.trim();
        return paymentRepository.search(effective, normalised, page).map(PaymentViewMapper::toSummary);
    }

    /** The seller's payment counters, in one place so the dashboard and reports agree. */
    public PaymentCountersView counters() {
        long paid = paymentRepository.countByStatus(PaymentStatus.PAID);
        long partial = paymentRepository.countByStatus(PaymentStatus.PARTIALLY_REFUNDED);
        return new PaymentCountersView(
                paymentRepository.count(),
                paid + partial,
                paymentRepository.countByStatus(PaymentStatus.PENDING)
                        + paymentRepository.countByStatus(PaymentStatus.CREATED),
                paymentRepository.countByStatus(PaymentStatus.FAILED),
                paymentRepository.countByStatus(PaymentStatus.REFUNDED),
                paymentRepository.countByStatus(PaymentStatus.DISPUTED));
    }
}
