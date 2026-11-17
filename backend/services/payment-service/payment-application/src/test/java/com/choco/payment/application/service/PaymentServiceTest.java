package com.choco.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.ForbiddenException;
import com.choco.core.error.IdempotencyConflictException;
import com.choco.payment.application.command.InitiatePaymentCommand;
import com.choco.payment.application.command.VerifyPaymentCommand;
import com.choco.payment.application.dto.PaymentInitiationView;
import com.choco.payment.application.dto.PaymentView;
import com.choco.payment.application.support.FakePaymentGateway;
import com.choco.payment.application.support.InMemoryIdempotencyRepository;
import com.choco.payment.application.support.InMemoryPaymentRepository;
import com.choco.payment.application.support.RecordingAuditLog;
import com.choco.payment.domain.payment.PaymentStatus;
import com.choco.test.Fakes;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Payment service")
class PaymentServiceTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();
    private static final long AMOUNT = 49900;

    private InMemoryPaymentRepository payments;
    private FakePaymentGateway gateway;
    private Fakes.RecordingEventPublisher events;
    private RecordingAuditLog audit;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        payments = new InMemoryPaymentRepository();
        gateway = new FakePaymentGateway();
        events = new Fakes.RecordingEventPublisher();
        audit = new RecordingAuditLog();
        service = new PaymentService(payments, new InMemoryIdempotencyRepository(), gateway, events,
                audit, Fakes.FixedClock.startingAt("2026-01-01T10:00:00Z"),
                new Fakes.SequentialIdGenerator(), new Fakes.DirectTransactionRunner());
    }

    private PaymentInitiationView initiate() {
        return service.initiate(new InitiatePaymentCommand(ORDER, CUSTOMER, AMOUNT, "INR", "rcpt",
                "idem-1"));
    }

    @Test
    @DisplayName("initiation creates a provider order and returns the publishable key")
    void initiationReturnsProviderOrder() {
        PaymentInitiationView view = initiate();

        assertThat(view.provider()).isEqualTo("razorpay");
        assertThat(view.providerOrderId()).isNotBlank();
        assertThat(view.amountMinorUnits()).isEqualTo(AMOUNT);
        assertThat(view.currency()).isEqualTo("INR");
        assertThat(view.publicKey()).isEqualTo("rzp_test_key");
        assertThat(audit.hasAction("payment.initiate")).isTrue();
    }

    @Test
    @DisplayName("a second initiation for the same order reuses the first provider order")
    void initiationIsIdempotentPerOrder() {
        PaymentInitiationView first = initiate();
        PaymentInitiationView second = service.initiate(
                new InitiatePaymentCommand(ORDER, CUSTOMER, AMOUNT, "INR", "rcpt", "idem-2"));

        assertThat(second.providerOrderId()).isEqualTo(first.providerOrderId());
        assertThat(payments.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a replayed idempotency key for the same order returns the same provider order")
    void replayWithSameKeyIsIdempotent() {
        PaymentInitiationView first = initiate();
        PaymentInitiationView replay = service.initiate(
                new InitiatePaymentCommand(ORDER, CUSTOMER, AMOUNT, "INR", "rcpt", "idem-1"));

        assertThat(replay.providerOrderId()).isEqualTo(first.providerOrderId());
        assertThat(payments.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a replayed idempotency key for a different order is refused")
    void replayWithDifferentOrderIsRefused() {
        initiate();

        assertThatThrownBy(() -> service.initiate(
                new InitiatePaymentCommand(UUID.randomUUID(), CUSTOMER, AMOUNT, "INR", "rcpt", "idem-1")))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("a non-positive amount is refused")
    void nonPositiveAmountRefused() {
        assertThatThrownBy(() -> service.initiate(
                new InitiatePaymentCommand(ORDER, CUSTOMER, 0, "INR", "rcpt", null)))
                .isInstanceOf(com.choco.core.error.ValidationException.class);
    }

    @Test
    @DisplayName("verification of a captured payment marks it paid and raises an event")
    void verifyCapturedMarksPaid() {
        initiate();
        gateway.report(new FakePaymentGateway.ProviderPayment("pay_1", "order_fake_1", AMOUNT, "INR",
                true, "captured", "upi", null, null, null));

        PaymentView view = service.verify(CUSTOMER, new VerifyPaymentCommand(ORDER, "order_fake_1",
                "pay_1", "sig"));

        assertThat(view.status()).isEqualTo(PaymentStatus.PAID.name());
        assertThat(view.providerPaymentId()).isEqualTo("pay_1");
        assertThat(events.eventsOfType("payment.captured")).hasSize(1);
    }

    @Test
    @DisplayName("a forged signature is refused before any state changes")
    void forgedSignatureRefused() {
        initiate();
        gateway.setCheckoutSignatureValid(false);

        assertThatThrownBy(() -> service.verify(CUSTOMER, new VerifyPaymentCommand(ORDER, "order_fake_1",
                "pay_1", "forged")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("could not be verified");

        assertThat(payments.findByOrderId(ORDER)).get()
                .extracting(p -> p.status())
                .isEqualTo(PaymentStatus.FAILED);
        assertThat(events.eventsOfType("payment.captured")).isEmpty();
    }

    @Test
    @DisplayName("an unreachable provider leaves the payment pending rather than failed")
    void unreachableProviderLeavesPending() {
        initiate();
        gateway.setUnreachable(true);

        assertThatThrownBy(() -> service.verify(CUSTOMER, new VerifyPaymentCommand(ORDER, "order_fake_1",
                "pay_1", "sig")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("could not be confirmed");

        assertThat(payments.findByOrderId(ORDER)).get()
                .extracting(p -> p.status())
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("an authorised-but-not-captured payment is recorded as pending, not paid")
    void authorisedIsPending() {
        initiate();
        gateway.report(new FakePaymentGateway.ProviderPayment("pay_1", "order_fake_1", AMOUNT, "INR",
                false, "authorized", "upi", null, null, null));

        PaymentView view = service.verify(CUSTOMER, new VerifyPaymentCommand(ORDER, "order_fake_1",
                "pay_1", "sig"));

        assertThat(view.status()).isEqualTo(PaymentStatus.PENDING.name());
        assertThat(events.eventsOfType("payment.captured")).isEmpty();
    }

    @Test
    @DisplayName("a failed payment is recorded as failed with its reason")
    void failedPaymentRecorded() {
        initiate();
        gateway.report(new FakePaymentGateway.ProviderPayment("pay_1", "order_fake_1", AMOUNT, "INR",
                false, "failed", "upi", "insufficient funds", null, null));

        PaymentView view = service.verify(CUSTOMER, new VerifyPaymentCommand(ORDER, "order_fake_1",
                "pay_1", "sig"));

        assertThat(view.status()).isEqualTo(PaymentStatus.FAILED.name());
        assertThat(events.eventsOfType("payment.failed")).hasSize(1);
    }

    @Test
    @DisplayName("a customer cannot verify another customer's payment")
    void cannotVerifyAnothersPayment() {
        initiate();

        assertThatThrownBy(() -> service.verify(OTHER, new VerifyPaymentCommand(ORDER, "order_fake_1",
                "pay_1", "sig")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("a repeated verification of an already-paid payment is a no-op")
    void repeatedVerificationIsNoOp() {
        initiate();
        gateway.report(new FakePaymentGateway.ProviderPayment("pay_1", "order_fake_1", AMOUNT, "INR",
                true, "captured", "upi", null, null, null));
        service.verify(CUSTOMER, new VerifyPaymentCommand(ORDER, "order_fake_1", "pay_1", "sig"));
        int capturedBefore = events.eventsOfType("payment.captured").size();

        PaymentView view = service.verify(CUSTOMER, new VerifyPaymentCommand(ORDER, "order_fake_1",
                "pay_1", "sig"));

        assertThat(view.status()).isEqualTo(PaymentStatus.PAID.name());
        assertThat(events.eventsOfType("payment.captured")).hasSize(capturedBefore);
    }

    @Test
    @DisplayName("verifying an order with no payment is a not-found")
    void verifyWithoutPaymentNotFound() {
        assertThatThrownBy(() -> service.verify(CUSTOMER, new VerifyPaymentCommand(ORDER, "o", "p", "s")))
                .isInstanceOf(com.choco.core.error.NotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("a captured amount that does not match is refused")
    void mismatchedCaptureRefused() {
        initiate();
        gateway.report(new FakePaymentGateway.ProviderPayment("pay_1", "order_fake_1", 100, "INR",
                true, "captured", "upi", null, null, null));

        assertThatThrownBy(() -> service.verify(CUSTOMER, new VerifyPaymentCommand(ORDER, "order_fake_1",
                "pay_1", "sig")))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_VERIFICATION_FAILED);
    }
}
