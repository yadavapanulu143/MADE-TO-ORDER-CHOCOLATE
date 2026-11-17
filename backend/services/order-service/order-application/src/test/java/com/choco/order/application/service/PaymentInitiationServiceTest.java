package com.choco.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.Money;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import com.choco.core.domain.Quantity;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.DomainException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.ForbiddenException;
import com.choco.core.error.NotFoundException;
import com.choco.order.application.dto.PaymentInitiationView;
import com.choco.order.application.support.InMemoryOrderRepository;
import com.choco.order.application.support.RecordingAuditLog;
import com.choco.order.application.support.StubPayment;
import com.choco.order.domain.order.CustomerSnapshot;
import com.choco.order.domain.order.DeliveryAddress;
import com.choco.order.domain.order.Order;
import com.choco.order.domain.order.OrderLine;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Starting a payment for an order.
 *
 * <p>These tests pin the two things that make this use case safe: the amount collected is the order's
 * own frozen total and never anything a caller supplies, and only the order's owner may start a
 * payment for it. Both are the kind of rule that is easy to get subtly wrong and expensive to get
 * wrong — one charges the wrong money, the other charges someone else's.
 */
@DisplayName("Payment initiation service")
class PaymentInitiationServiceTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID VARIANT = UUID.randomUUID();

    private InMemoryOrderRepository orders;
    private StubPayment payments;
    private RecordingAuditLog audit;
    private PaymentInitiationService service;

    @BeforeEach
    void setUp() {
        orders = new InMemoryOrderRepository();
        payments = new StubPayment();
        audit = new RecordingAuditLog();
        service = new PaymentInitiationService(orders, payments, audit);
    }

    @Test
    @DisplayName("starts a payment for the order's own total, not any caller-supplied figure")
    void initiatesForTheOrdersTotal() {
        Order order = seed(CUSTOMER);

        PaymentInitiationView view = service.initiate(CUSTOMER, order.id(), "key-1");

        // 49900 items + 5000 delivery = 54900, from the order's frozen snapshot.
        assertThat(payments.lastCall().amount()).isEqualTo(Money.inr(54900));
        assertThat(view.amountMinorUnits()).isEqualTo(54900);
        assertThat(view.providerOrderId()).isEqualTo("order_stub_1");
        assertThat(view.publicKey()).isEqualTo("rzp_test_key");
        assertThat(view.receipt()).isEqualTo(order.orderNumber());
    }

    @Test
    @DisplayName("forwards the idempotency key so a retry reuses the first provider order")
    void forwardsTheIdempotencyKey() {
        Order order = seed(CUSTOMER);
        service.initiate(CUSTOMER, order.id(), "key-abc");
        assertThat(payments.lastCall().idempotencyKey()).isEqualTo("key-abc");
    }

    @Test
    @DisplayName("a customer cannot start a payment for another customer's order")
    void refusesAnotherCustomersOrder() {
        Order order = seed(CUSTOMER);
        assertThatThrownBy(() -> service.initiate(OTHER, order.id(), "key-1"))
                .isInstanceOf(ForbiddenException.class);
        assertThat(payments.calls()).as("no call reaches the payment service").isEmpty();
    }

    @Test
    @DisplayName("a paid order cannot be charged again")
    void refusesAnAlreadyPaidOrder() {
        Order order = seed(CUSTOMER);
        order.markPaid("pay_1", "order_1", Money.inr(54900), Instant.now());
        orders.save(order);

        assertThatThrownBy(() -> service.initiate(CUSTOMER, order.id(), "key-1"))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).errorCode())
                        .isEqualTo(ErrorCode.PAYMENT_ALREADY_CAPTURED));
        assertThat(payments.calls()).isEmpty();
    }

    @Test
    @DisplayName("a cancelled order cannot be paid")
    void refusesACancelledOrder() {
        Order order = seed(CUSTOMER);
        order.cancel("changed my mind", CUSTOMER.toString(), Instant.now());
        orders.save(order);

        assertThatThrownBy(() -> service.initiate(CUSTOMER, order.id(), "key-1"))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(payments.calls()).isEmpty();
    }

    @Test
    @DisplayName("a failed payment can be started again")
    void allowsRetryAfterFailure() {
        Order order = seed(CUSTOMER);
        order.markPaymentFailed("card declined", Instant.now());
        orders.save(order);

        PaymentInitiationView view = service.initiate(CUSTOMER, order.id(), "key-1");

        assertThat(view.providerOrderId()).isEqualTo("order_stub_1");
    }

    @Test
    @DisplayName("an unknown order is a not-found")
    void unknownOrder() {
        assertThatThrownBy(() -> service.initiate(CUSTOMER, UUID.randomUUID(), "key-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("a payment service outage surfaces as a dependency error, not a success")
    void dependencyOutage() {
        Order order = seed(CUSTOMER);
        payments.failingWith(new DomainException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                "The payment service is temporarily unavailable. Please retry shortly."));

        assertThatThrownBy(() -> service.initiate(CUSTOMER, order.id(), "key-1"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).errorCode())
                        .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE));
    }

    @Test
    @DisplayName("starting a payment is audited against the order")
    void auditsTheInitiation() {
        Order order = seed(CUSTOMER);
        service.initiate(CUSTOMER, order.id(), "key-1");

        assertThat(audit.hasAction("order.payment.initiate")).isTrue();
        assertThat(audit.entries().get(0).targetId()).isEqualTo(order.id().toString());
    }

    @Test
    @DisplayName("the order is not modified by starting a payment; only a verified event may confirm it")
    void doesNotConfirmTheOrder() {
        Order order = seed(CUSTOMER);
        service.initiate(CUSTOMER, order.id(), "key-1");

        Order stored = orders.findById(order.id()).orElseThrow();
        assertThat(stored.status().name()).isEqualTo("PAYMENT_PENDING");
        assertThat(stored.paymentStatus().name()).isEqualTo("PENDING");
    }

    private Order seed(UUID customerId) {
        Order order = Order.place(UUID.randomUUID(), "CHOCO-2026001-1000", customerId,
                new CustomerSnapshot("Asha Rao", EmailAddress.of("asha@example.com"),
                        PhoneNumber.of("9876543210")),
                new DeliveryAddress("Asha Rao", PhoneNumber.of("9876543210"), "12 Cocoa Lane", null,
                        "Bengaluru", "Karnataka", Pincode.of("560001"), null),
                List.of(new OrderLine(PRODUCT, VARIANT, "Dark Chocolate", "200 g", "SKU-1",
                        Money.inr(49900), Quantity.of(1))),
                Money.inr(5000), Instant.parse("2026-01-01T10:00:00Z"));
        return orders.save(order);
    }
}
