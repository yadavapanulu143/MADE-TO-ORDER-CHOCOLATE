package com.choco.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.Money;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import com.choco.core.domain.Quantity;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ForbiddenException;
import com.choco.core.error.NotFoundException;
import com.choco.order.application.command.AddOrderNoteCommand;
import com.choco.order.application.command.AssignDeliveryCommand;
import com.choco.order.application.command.CancelOrderCommand;
import com.choco.order.application.command.DeliveryProgressCommand;
import com.choco.order.application.command.PreparationProgressCommand;
import com.choco.order.application.command.RequestRefundCommand;
import com.choco.order.application.dto.OrderView;
import com.choco.order.application.support.InMemoryOrderRepository;
import com.choco.order.application.support.RecordingAuditLog;
import com.choco.order.domain.order.CustomerSnapshot;
import com.choco.order.domain.order.DeliveryAddress;
import com.choco.order.domain.order.Order;
import com.choco.order.domain.order.OrderLine;
import com.choco.test.Fakes;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Order command service")
class OrderCommandServiceTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID VARIANT = UUID.randomUUID();
    private static final String ADMIN = "admin-1";

    private InMemoryOrderRepository orders;
    private RecordingAuditLog audit;
    private Fakes.RecordingEventPublisher events;
    private OrderCommandService service;

    @BeforeEach
    void setUp() {
        orders = new InMemoryOrderRepository();
        audit = new RecordingAuditLog();
        events = new Fakes.RecordingEventPublisher();
        service = new OrderCommandService(orders, events, audit,
                Fakes.FixedClock.startingAt("2026-01-01T10:00:00Z"),
                new Fakes.DirectTransactionRunner());
    }

    @Test
    @DisplayName("a customer cancels their own unpaid order")
    void customerCancelsOwn() {
        Order order = seed(CUSTOMER);
        OrderView view = service.cancelOwnOrder(CUSTOMER,
                new CancelOrderCommand(order.id(), "changed my mind"));

        assertThat(view.status()).isEqualTo("CANCELLED");
        assertThat(audit.hasAction("order.cancel")).isTrue();
        assertThat(events.events()).anyMatch(e -> e.eventType().equals("order.cancelled"));
    }

    @Test
    @DisplayName("a customer cannot cancel another customer's order")
    void customerCannotCancelOthers() {
        Order order = seed(CUSTOMER);
        assertThatThrownBy(() -> service.cancelOwnOrder(OTHER, new CancelOrderCommand(order.id(), "x")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("the seller cancels any order and the act is audited")
    void adminCancels() {
        Order order = seed(CUSTOMER);
        OrderView view = service.cancelAsAdmin(ADMIN, new CancelOrderCommand(order.id(), "cannot fulfil"));

        assertThat(view.status()).isEqualTo("CANCELLED");
        assertThat(audit.entries().get(0).actorType()).isEqualTo("ADMIN");
        assertThat(audit.entries().get(0).actorId()).isEqualTo(ADMIN);
    }

    @Test
    @DisplayName("the full seller lifecycle advances the order to delivered")
    void fullLifecycle() {
        Order order = seed(CUSTOMER);
        service.markPaid(order.id(), "pay_1", "order_1");

        assertThat(service.advancePreparation(ADMIN, prep(order, PreparationProgressCommand.PreparationAction.START)).status())
                .isEqualTo("PREPARATION_STARTED");
        service.advancePreparation(ADMIN, prep(order, PreparationProgressCommand.PreparationAction.COMPLETE));
        service.advancePreparation(ADMIN, prep(order, PreparationProgressCommand.PreparationAction.PACKAGING));
        assertThat(service.advancePreparation(ADMIN, prep(order, PreparationProgressCommand.PreparationAction.READY_FOR_DISPATCH)).status())
                .isEqualTo("READY_FOR_DISPATCH");

        assertThat(service.assignDelivery(ADMIN, new AssignDeliveryCommand(order.id(), "Ravi", "9876500000")).status())
                .isEqualTo("READY_FOR_DISPATCH");
        assertThat(service.advanceDelivery(ADMIN,
                delivery(order, DeliveryProgressCommand.DeliveryAction.OUT_FOR_DELIVERY)).status())
                .isEqualTo("OUT_FOR_DELIVERY");
        assertThat(service.advanceDelivery(ADMIN, delivery(order, DeliveryProgressCommand.DeliveryAction.DELIVERED)).status())
                .isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("preparation cannot start before payment is verified")
    void preparationRequiresPayment() {
        Order order = seed(CUSTOMER);
        assertThatThrownBy(() -> service.advancePreparation(ADMIN,
                prep(order, PreparationProgressCommand.PreparationAction.START)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("holding and resuming preparation works")
    void holdAndResume() {
        Order order = seed(CUSTOMER);
        service.markPaid(order.id(), "pay_1", "order_1");
        service.advancePreparation(ADMIN, prep(order, PreparationProgressCommand.PreparationAction.START));

        assertThat(service.advancePreparation(ADMIN,
                new PreparationProgressCommand(order.id(), PreparationProgressCommand.PreparationAction.HOLD,
                        "out of cocoa")).status())
                .isEqualTo("PREPARATION_ON_HOLD");
        assertThat(service.advancePreparation(ADMIN,
                prep(order, PreparationProgressCommand.PreparationAction.RESUME)).status())
                .isEqualTo("PREPARATION_STARTED");
    }

    @Test
    @DisplayName("a failed delivery attempt can be rescheduled then delivered")
    void failedRescheduledDelivered() {
        Order order = readyForDispatch();
        service.assignDelivery(ADMIN, new AssignDeliveryCommand(order.id(), "Ravi", null));
        service.advanceDelivery(ADMIN, delivery(order, DeliveryProgressCommand.DeliveryAction.OUT_FOR_DELIVERY));

        assertThat(service.advanceDelivery(ADMIN,
                new DeliveryProgressCommand(order.id(), DeliveryProgressCommand.DeliveryAction.ATTEMPT_FAILED,
                        "nobody home")).status())
                .isEqualTo("DELIVERY_ATTEMPT_FAILED");
        assertThat(service.advanceDelivery(ADMIN,
                delivery(order, DeliveryProgressCommand.DeliveryAction.RESCHEDULED)).status())
                .isEqualTo("DELIVERY_RESCHEDULED");
        service.advanceDelivery(ADMIN, delivery(order, DeliveryProgressCommand.DeliveryAction.OUT_FOR_DELIVERY));
        assertThat(service.advanceDelivery(ADMIN, delivery(order, DeliveryProgressCommand.DeliveryAction.DELIVERED)).status())
                .isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("a paid order can be refunded and the request is audited")
    void refund() {
        Order order = seed(CUSTOMER);
        service.markPaid(order.id(), "pay_1", "order_1");

        OrderView view = service.requestRefund(CUSTOMER, new RequestRefundCommand(order.id(), "damaged"));

        assertThat(view.status()).isEqualTo("REFUND_UNDER_REVIEW");
        assertThat(view.paymentStatus()).isEqualTo("REFUND_UNDER_REVIEW");
        assertThat(audit.hasAction("order.refund-request")).isTrue();
        assertThat(events.events()).anyMatch(e -> e.eventType().equals("order.refund-requested"));
    }

    @Test
    @DisplayName("marking paid is idempotent and publishes confirmation once")
    void markPaidIdempotent() {
        Order order = seed(CUSTOMER);
        service.markPaid(order.id(), "pay_1", "order_1");
        events.clear();

        service.markPaid(order.id(), "pay_1", "order_1");

        assertThat(events.events()).isEmpty();
        assertThat(orders.findById(order.id()).orElseThrow().status().name()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("a payment failure is recorded and leaves preparation locked")
    void paymentFailure() {
        Order order = seed(CUSTOMER);
        service.markPaymentFailed(order.id(), "card declined");

        Order stored = orders.findById(order.id()).orElseThrow();
        assertThat(stored.status().name()).isEqualTo("PAYMENT_FAILED");
        assertThat(stored.paymentStatus().name()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("a failed payment can be retried and then confirmed")
    void retryPayment() {
        Order order = seed(CUSTOMER);
        service.markPaymentFailed(order.id(), "card declined");
        OrderView retried = service.allowPaymentRetry(CUSTOMER.toString(), order.id());
        assertThat(retried.status()).isEqualTo("PAYMENT_PENDING");

        service.markPaid(order.id(), "pay_2", "order_2");
        assertThat(orders.findById(order.id()).orElseThrow().status().name()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("marking refunded moves the order to REFUNDED")
    void markRefunded() {
        Order order = seed(CUSTOMER);
        service.markPaid(order.id(), "pay_1", "order_1");
        service.requestRefund(CUSTOMER, new RequestRefundCommand(order.id(), "damaged"));

        service.markRefunded(order.id());

        Order stored = orders.findById(order.id()).orElseThrow();
        assertThat(stored.status().name()).isEqualTo("REFUNDED");
        assertThat(stored.paymentStatus().name()).isEqualTo("REFUNDED");
    }

    @Test
    @DisplayName("adding a note appends it and audits the act")
    void addNote() {
        Order order = seed(CUSTOMER);
        OrderView view = service.addNote(ADMIN, new AddOrderNoteCommand(order.id(), "pack with care"));

        assertThat(view.notes()).hasSize(1);
        assertThat(view.notes().get(0).text()).isEqualTo("pack with care");
        assertThat(audit.hasAction("order.note.add")).isTrue();
    }

    @Test
    @DisplayName("operating on a missing order is a not-found")
    void missingOrder() {
        assertThatThrownBy(() -> service.cancelAsAdmin(ADMIN,
                new CancelOrderCommand(UUID.randomUUID(), "x")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("recording verification-pending does not confirm the order")
    void verificationPending() {
        Order order = seed(CUSTOMER);
        service.markPaymentVerificationPending(order.id(), "pay_1", "order_1");

        Order stored = orders.findById(order.id()).orElseThrow();
        assertThat(stored.status().name()).isEqualTo("PAYMENT_VERIFICATION_PENDING");
        assertThat(stored.paymentStatus().name()).isEqualTo("VERIFICATION_PENDING");
    }

    private PreparationProgressCommand prep(Order order,
                                            PreparationProgressCommand.PreparationAction action) {
        return new PreparationProgressCommand(order.id(), action, null);
    }

    private DeliveryProgressCommand delivery(Order order,
                                             DeliveryProgressCommand.DeliveryAction action) {
        return new DeliveryProgressCommand(order.id(), action, null);
    }

    private Order readyForDispatch() {
        Order order = seed(CUSTOMER);
        service.markPaid(order.id(), "pay_1", "order_1");
        service.advancePreparation(ADMIN, prep(order, PreparationProgressCommand.PreparationAction.START));
        service.advancePreparation(ADMIN, prep(order, PreparationProgressCommand.PreparationAction.COMPLETE));
        service.advancePreparation(ADMIN, prep(order, PreparationProgressCommand.PreparationAction.PACKAGING));
        service.advancePreparation(ADMIN, prep(order, PreparationProgressCommand.PreparationAction.READY_FOR_DISPATCH));
        return order;
    }

    private Order seed(UUID customerId) {
        Order order = Order.place(UUID.randomUUID(), "CHOCO-" + Math.abs(UUID.randomUUID().hashCode()),
                customerId,
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
