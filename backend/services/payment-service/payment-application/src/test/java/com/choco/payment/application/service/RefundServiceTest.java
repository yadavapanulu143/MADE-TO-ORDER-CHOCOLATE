package com.choco.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.BusinessRuleException;
import com.choco.payment.application.command.InitiatePaymentCommand;
import com.choco.payment.application.command.RequestRefundCommand;
import com.choco.payment.application.dto.RefundResultView;
import com.choco.payment.application.support.FakePaymentGateway;
import com.choco.payment.application.support.InMemoryIdempotencyRepository;
import com.choco.payment.application.support.InMemoryPaymentRepository;
import com.choco.payment.application.support.RecordingAuditLog;
import com.choco.payment.domain.payment.Payment;
import com.choco.payment.domain.payment.PaymentStatus;
import com.choco.test.Fakes;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Refund service")
class RefundServiceTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();
    private static final long AMOUNT = 49900;

    private InMemoryPaymentRepository payments;
    private FakePaymentGateway gateway;
    private Fakes.RecordingEventPublisher events;
    private RecordingAuditLog audit;
    private PaymentService paymentService;
    private RefundService refundService;

    @BeforeEach
    void setUp() {
        payments = new InMemoryPaymentRepository();
        gateway = new FakePaymentGateway();
        events = new Fakes.RecordingEventPublisher();
        audit = new RecordingAuditLog();
        var clock = Fakes.FixedClock.startingAt("2026-01-01T10:00:00Z");
        var ids = new Fakes.SequentialIdGenerator();
        paymentService = new PaymentService(payments, new InMemoryIdempotencyRepository(), gateway, events,
                audit, clock, ids, new Fakes.DirectTransactionRunner());
        refundService = new RefundService(payments, gateway, audit, clock, ids,
                new Fakes.DirectTransactionRunner());
    }

    private Payment paidPayment() {
        paymentService.initiate(new InitiatePaymentCommand(ORDER, CUSTOMER, AMOUNT, "INR", "rcpt", null));
        Payment payment = payments.findByOrderId(ORDER).orElseThrow();
        payment.markCaptured("pay_1", com.choco.payment.domain.payment.PaymentMethod.UPI,
                com.choco.core.domain.Money.inr(AMOUNT), java.time.Instant.now());
        payments.save(payment);
        return payment;
    }

    @Test
    @DisplayName("a full refund moves the payment to refunded and is audited")
    void fullRefund() {
        Payment payment = paidPayment();

        RefundResultView result = refundService.refund(new RequestRefundCommand(payment.id(), AMOUNT,
                "customer request", "admin-1"));

        assertThat(result.status()).isEqualTo("PROCESSED");
        assertThat(result.providerRefundId()).isNotBlank();
        assertThat(payments.findById(payment.id())).get()
                .extracting(Payment::status)
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(audit.hasAction("payment.refund")).isTrue();
    }

    @Test
    @DisplayName("a partial refund leaves the payment partially refunded")
    void partialRefund() {
        Payment payment = paidPayment();

        refundService.refund(new RequestRefundCommand(payment.id(), 20000, "partial", "admin-1"));

        assertThat(payments.findById(payment.id())).get()
                .extracting(Payment::status)
                .isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    }

    @Test
    @DisplayName("a refund above what was captured is refused by the aggregate")
    void refundAboveCapturedRefused() {
        Payment payment = paidPayment();

        assertThatThrownBy(() -> refundService.refund(new RequestRefundCommand(payment.id(), AMOUNT + 1,
                "too much", "admin-1")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("a provider refusal is recorded and surfaced")
    void providerRefusalRecorded() {
        Payment payment = paidPayment();
        gateway.setRefundSucceeds(false);

        assertThatThrownBy(() -> refundService.refund(new RequestRefundCommand(payment.id(), AMOUNT,
                "customer request", "admin-1")))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(payments.findById(payment.id())).get()
                .extracting(Payment::status)
                .isEqualTo(PaymentStatus.PAID);
        assertThat(audit.hasAction("payment.refund")).isTrue();
    }

    @Test
    @DisplayName("an unknown payment cannot be refunded")
    void unknownPaymentRefused() {
        assertThatThrownBy(() -> refundService.refund(new RequestRefundCommand(UUID.randomUUID(), AMOUNT,
                "x", "admin-1")))
                .isInstanceOf(com.choco.core.error.NotFoundException.class);
    }
}
