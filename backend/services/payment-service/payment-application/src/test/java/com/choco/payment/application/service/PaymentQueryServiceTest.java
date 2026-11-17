package com.choco.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.Money;
import com.choco.core.error.NotFoundException;
import com.choco.core.port.PageQuery;
import com.choco.payment.application.command.InitiatePaymentCommand;
import com.choco.payment.application.dto.PaymentCountersView;
import com.choco.payment.application.support.FakePaymentGateway;
import com.choco.payment.application.support.InMemoryIdempotencyRepository;
import com.choco.payment.application.support.InMemoryPaymentRepository;
import com.choco.payment.application.support.RecordingAuditLog;
import com.choco.payment.domain.payment.Payment;
import com.choco.payment.domain.payment.PaymentMethod;
import com.choco.payment.domain.payment.PaymentStatus;
import com.choco.test.Fakes;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Payment query service")
class PaymentQueryServiceTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final long AMOUNT = 49900;

    private InMemoryPaymentRepository payments;
    private PaymentService paymentService;
    private PaymentQueryService queryService;

    @BeforeEach
    void setUp() {
        payments = new InMemoryPaymentRepository();
        paymentService = new PaymentService(payments, new InMemoryIdempotencyRepository(),
                new FakePaymentGateway(), new Fakes.RecordingEventPublisher(), new RecordingAuditLog(),
                Fakes.FixedClock.startingAt("2026-01-01T10:00:00Z"), new Fakes.SequentialIdGenerator(),
                new Fakes.DirectTransactionRunner());
        queryService = new PaymentQueryService(payments);
    }

    private Payment paid(UUID customerId) {
        UUID orderId = UUID.randomUUID();
        paymentService.initiate(new InitiatePaymentCommand(orderId, customerId, AMOUNT, "INR", "r", null));
        Payment payment = payments.findByOrderId(orderId).orElseThrow();
        payment.markCaptured("pay_" + payment.id(), PaymentMethod.UPI, Money.inr(AMOUNT), Instant.now());
        return payments.save(payment);
    }

    @Test
    @DisplayName("a customer reads their own payment")
    void ownPayment() {
        Payment payment = paid(CUSTOMER);

        assertThat(queryService.ownPayment(CUSTOMER, payment.id()).id()).isEqualTo(payment.id());
    }

    @Test
    @DisplayName("a customer cannot read another customer's payment")
    void cannotReadAnothers() {
        Payment payment = paid(CUSTOMER);

        assertThatThrownBy(() -> queryService.ownPayment(OTHER, payment.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("the payment for an order is scoped to its owner")
    void ownPaymentForOrderScoped() {
        Payment payment = paid(CUSTOMER);

        assertThatThrownBy(() -> queryService.ownPaymentForOrder(OTHER, payment.orderId()))
                .isInstanceOf(NotFoundException.class);
        assertThat(queryService.ownPaymentForOrder(CUSTOMER, payment.orderId()).id())
                .isEqualTo(payment.id());
    }

    @Test
    @DisplayName("a customer's history contains only their own payments")
    void historyScopedToOwner() {
        paid(CUSTOMER);
        paid(OTHER);

        var page = queryService.ownHistory(CUSTOMER, PageQuery.of(0, 20, null, false));

        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("the seller searches across all payments")
    void adminSearch() {
        paid(CUSTOMER);
        paid(OTHER);

        var page = queryService.searchForAdmin(null, null, PageQuery.of(0, 20, null, false));

        assertThat(page.content()).hasSize(2);
    }

    @Test
    @DisplayName("counters reflect the payment states")
    void counters() {
        paid(CUSTOMER);
        paid(OTHER);

        PaymentCountersView counters = queryService.counters();

        assertThat(counters.total()).isEqualTo(2);
        assertThat(counters.paid()).isEqualTo(2);
    }
}
