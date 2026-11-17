package com.choco.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.payment.application.command.InitiatePaymentCommand;
import com.choco.payment.application.support.FakePaymentGateway;
import com.choco.payment.application.support.InMemoryIdempotencyRepository;
import com.choco.payment.application.support.InMemoryPaymentRepository;
import com.choco.payment.application.support.RecordingAuditLog;
import com.choco.payment.domain.payment.Payment;
import com.choco.payment.domain.payment.PaymentStatus;
import com.choco.payment.domain.port.PaymentGatewayPort.WebhookEvent;
import com.choco.test.Fakes;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Payment webhook service")
class PaymentWebhookServiceTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();
    private static final long AMOUNT = 49900;

    private InMemoryPaymentRepository payments;
    private FakePaymentGateway gateway;
    private Fakes.RecordingEventPublisher events;
    private RecordingAuditLog audit;
    private PaymentService paymentService;
    private PaymentWebhookService webhookService;

    @BeforeEach
    void setUp() {
        payments = new InMemoryPaymentRepository();
        gateway = new FakePaymentGateway();
        events = new Fakes.RecordingEventPublisher();
        audit = new RecordingAuditLog();
        paymentService = new PaymentService(payments, new InMemoryIdempotencyRepository(), gateway, events,
                audit, Fakes.FixedClock.startingAt("2026-01-01T10:00:00Z"),
                new Fakes.SequentialIdGenerator(), new Fakes.DirectTransactionRunner());
        webhookService = new PaymentWebhookService(payments, gateway, paymentService, audit,
                Fakes.FixedClock.startingAt("2026-01-01T10:00:00Z"), new Fakes.DirectTransactionRunner());
    }

    private Payment initiate() {
        paymentService.initiate(new InitiatePaymentCommand(ORDER, CUSTOMER, AMOUNT, "INR", "rcpt", null));
        return payments.findByOrderId(ORDER).orElseThrow();
    }

    @Test
    @DisplayName("an unsigned webhook is refused and nothing changes")
    void unsignedWebhookRefused() {
        Payment payment = initiate();
        gateway.setWebhookSignatureValid(false);

        assertThatThrownBy(() -> webhookService.handle("{}", "bad-signature"))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_SIGNATURE_INVALID);

        assertThat(payments.findById(payment.id())).get()
                .extracting(Payment::status)
                .isEqualTo(PaymentStatus.CREATED);
        assertThat(events.events()).isEmpty();
    }

    @Test
    @DisplayName("a verified capture webhook marks the payment paid")
    void captureWebhookMarksPaid() {
        Payment payment = initiate();
        String providerOrderId = payment.providerOrderId();
        gateway.setNextWebhookEvent(new WebhookEvent("payment.captured", "pay_1", providerOrderId,
                AMOUNT, "INR", "upi", null, null, null));

        webhookService.handle("{}", "sig");

        assertThat(payments.findById(payment.id())).get()
                .extracting(Payment::status)
                .isEqualTo(PaymentStatus.PAID);
        assertThat(events.eventsOfType("payment.captured")).hasSize(1);
    }

    @Test
    @DisplayName("a duplicate capture webhook is idempotent and raises no second event")
    void duplicateCaptureIdempotent() {
        Payment payment = initiate();
        String providerOrderId = payment.providerOrderId();
        gateway.setNextWebhookEvent(new WebhookEvent("payment.captured", "pay_1", providerOrderId,
                AMOUNT, "INR", "upi", null, null, null));
        webhookService.handle("{}", "sig");

        webhookService.handle("{}", "sig");

        assertThat(events.eventsOfType("payment.captured")).hasSize(1);
    }

    @Test
    @DisplayName("a capture webhook for an unknown payment is acknowledged and ignored")
    void unknownPaymentIgnored() {
        gateway.setNextWebhookEvent(new WebhookEvent("payment.captured", "pay_unknown", "order_unknown",
                AMOUNT, "INR", "upi", null, null, null));

        webhookService.handle("{}", "sig");

        assertThat(events.events()).isEmpty();
    }

    @Test
    @DisplayName("a failure webhook records the failure")
    void failureWebhookRecords() {
        Payment payment = initiate();
        gateway.setNextWebhookEvent(new WebhookEvent("payment.failed", "pay_1", payment.providerOrderId(),
                AMOUNT, "INR", "upi", "declined", null, null));

        webhookService.handle("{}", "sig");

        assertThat(payments.findById(payment.id())).get()
                .extracting(Payment::status)
                .isEqualTo(PaymentStatus.FAILED);
        assertThat(events.eventsOfType("payment.failed")).hasSize(1);
    }

    @Test
    @DisplayName("an unhandled event type changes nothing")
    void unhandledEventIgnored() {
        Payment payment = initiate();
        gateway.setNextWebhookEvent(new WebhookEvent("payment.some.new.event", "pay_1",
                payment.providerOrderId(), AMOUNT, "INR", null, null, null, null));

        webhookService.handle("{}", "sig");

        assertThat(payments.findById(payment.id())).get()
                .extracting(Payment::status)
                .isEqualTo(PaymentStatus.CREATED);
    }
}
