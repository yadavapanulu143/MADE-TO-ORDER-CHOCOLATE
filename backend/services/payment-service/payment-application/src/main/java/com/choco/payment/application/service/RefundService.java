package com.choco.payment.application.service;

import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.port.Clock;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.TransactionRunner;
import com.choco.payment.application.command.RequestRefundCommand;
import com.choco.payment.application.dto.RefundResultView;
import com.choco.payment.application.port.AuditLogPort;
import com.choco.payment.domain.payment.Payment;
import com.choco.payment.domain.payment.Refund;
import com.choco.payment.domain.port.PaymentGatewayPort;
import com.choco.payment.domain.port.PaymentRepository;
import java.util.Map;
import java.util.UUID;

/**
 * Returns money to a customer, on the seller's decision.
 *
 * <p>A refund is a two-step fact: the seller decides, then the provider confirms. This service records
 * the decision and asks the provider to move the money; the payment only becomes {@code REFUNDED} when
 * the provider confirms it, which arrives either synchronously in the provider's reply or later as a
 * webhook. Marking it refunded on the decision alone would tell the customer their money is back
 * before it is.
 */
public class RefundService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayPort gateway;
    private final AuditLogPort auditLog;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionRunner transactions;

    public RefundService(PaymentRepository paymentRepository, PaymentGatewayPort gateway,
                         AuditLogPort auditLog, Clock clock, IdGenerator idGenerator,
                         TransactionRunner transactions) {
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.auditLog = auditLog;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.transactions = transactions;
    }

    /**
     * Records a refund and asks the provider to process it.
     *
     * <p>The amount is checked by the aggregate against what is still held, so a refund can never
     * exceed the captured amount however many times it is called.
     */
    public RefundResultView refund(RequestRefundCommand command) {
        return transactions.execute(() -> {
            Payment payment = paymentRepository.findById(command.paymentId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Payment not found"));
            if (payment.providerPaymentId() == null) {
                throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                        "This payment has no captured provider payment to refund");
            }
            Money amount = Money.inr(command.amountMinorUnits());
            Refund refund = payment.requestRefund(idGenerator.nextId(), amount, command.reason(),
                    command.actorId(), clock.now());

            PaymentGatewayPort.ProviderRefund providerRefund;
            try {
                providerRefund = gateway.refund(payment.providerPaymentId(), amount,
                        Map.of("paymentId", payment.id().toString()));
            } catch (BusinessRuleException ex) {
                // The provider refused or was unreachable. Record the attempt as failed so the seller
                // sees what happened rather than a refund that appears to have vanished.
                payment.markRefundFailed(refund.id(), ex.getMessage(), clock.now());
                paymentRepository.save(payment);
                audit(command.actorId(), "payment.refund", payment, "FAILURE", ex.getMessage());
                throw ex;
            }

            payment.markRefundProcessing(refund.id(), providerRefund.id(), clock.now());
            if ("processed".equalsIgnoreCase(providerRefund.status())) {
                payment.markRefundProcessed(refund.id(), providerRefund.id(), clock.now());
            }
            Payment saved = paymentRepository.save(payment);
            audit(command.actorId(), "payment.refund", saved, "SUCCESS",
                    providerRefund.id() + " " + providerRefund.status());
            Refund result = saved.refund(refund.id()).orElse(refund);
            return new RefundResultView(result.id(), saved.id(), result.amount().minorUnits(),
                    PaymentViewMapper.decimal(result.amount()), result.status().name(),
                    result.providerRefundId());
        });
    }

    private void audit(String actorId, String action, Payment payment, String outcome, String detail) {
        auditLog.record("ADMIN", actorId, action, "PAYMENT", payment.id().toString(), outcome, detail, null);
    }
}
