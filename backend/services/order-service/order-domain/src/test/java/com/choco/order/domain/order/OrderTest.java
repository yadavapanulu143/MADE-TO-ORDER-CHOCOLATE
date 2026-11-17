package com.choco.order.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.Money;
import com.choco.core.domain.Quantity;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.ForbiddenException;
import com.choco.order.domain.event.OrderCancelled;
import com.choco.order.domain.event.OrderConfirmed;
import com.choco.order.domain.event.OrderPlaced;
import com.choco.order.domain.event.OrderStatusChanged;
import com.choco.order.domain.event.RefundRequested;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Order aggregate")
class OrderTest {

    private static final Instant T0 = OrderTestSupport.T0;
    private static final Instant LATER = T0.plusSeconds(3600);

    @Nested
    @DisplayName("placing an order")
    class Placing {

        @Test
        @DisplayName("starts unpaid and pending, so nothing can be prepared yet")
        void startsPendingAndUnpaid() {
            Order order = OrderTestSupport.newOrder(OrderTestSupport.line(Money.inr(49900), 2));

            assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
            assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(order.preparationStatus()).isNull();
            assertThat(order.isPaid()).isFalse();
        }

        @Test
        @DisplayName("computes the item total, delivery charge and grand total exactly")
        void totalsAreExact() {
            Order order = OrderTestSupport.newOrder(
                    OrderTestSupport.line(Money.inr(49900), 2),
                    OrderTestSupport.line(UUID.randomUUID(), Money.inr(25050), 3));

            // 49900*2 + 25050*3 = 99800 + 75150 = 174950
            assertThat(order.itemsTotal()).isEqualTo(Money.inr(174_950));
            assertThat(order.deliveryCharge()).isEqualTo(Money.inr(5000));
            assertThat(order.total()).isEqualTo(Money.inr(179_950));
        }

        @Test
        @DisplayName("records the initial status history entry and the order-placed event")
        void recordsHistoryAndEvent() {
            Order order = OrderTestSupport.newOrder(OrderTestSupport.line(Money.inr(10000), 1));

            assertThat(order.statusHistory()).hasSize(1);
            assertThat(order.statusHistory().get(0).fromStatus()).isNull();
            assertThat(order.statusHistory().get(0).toStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
            assertThat(order.peekDomainEvents()).hasSize(1);
            assertThat(order.peekDomainEvents().get(0)).isInstanceOf(OrderPlaced.class);
        }

        @Test
        @DisplayName("rejects an order with no lines")
        void rejectsEmptyOrder() {
            assertThatThrownBy(() -> Order.place(UUID.randomUUID(), "CHOCO-1", OrderTestSupport.CUSTOMER,
                    OrderTestSupport.customer(), OrderTestSupport.address(), List.of(), Money.inr(0), T0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one line");
        }

        @Test
        @DisplayName("rejects more than the maximum number of lines")
        void rejectsTooManyLines() {
            List<OrderLine> lines = new ArrayList<>();
            for (int i = 0; i < Order.MAX_LINES + 1; i++) {
                lines.add(OrderTestSupport.line(UUID.randomUUID(), Money.inr(100), 1));
            }
            assertThatThrownBy(() -> Order.place(UUID.randomUUID(), "CHOCO-1", OrderTestSupport.CUSTOMER,
                    OrderTestSupport.customer(), OrderTestSupport.address(), lines, Money.inr(0), T0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at most");
        }

        @Test
        @DisplayName("rejects a negative delivery charge")
        void rejectsNegativeCharge() {
            assertThatThrownBy(() -> Order.place(UUID.randomUUID(), "CHOCO-1", OrderTestSupport.CUSTOMER,
                    OrderTestSupport.customer(), OrderTestSupport.address(),
                    List.of(OrderTestSupport.line(Money.inr(100), 1)), Money.inr(-1), T0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negative");
        }

        @Test
        @DisplayName("rejects a missing customer or address")
        void rejectsMissingParties() {
            assertThatThrownBy(() -> Order.place(UUID.randomUUID(), "CHOCO-1", null,
                    OrderTestSupport.customer(), OrderTestSupport.address(),
                    List.of(OrderTestSupport.line(Money.inr(100), 1)), Money.inr(0), T0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Order.place(UUID.randomUUID(), "CHOCO-1", OrderTestSupport.CUSTOMER,
                    OrderTestSupport.customer(), null,
                    List.of(OrderTestSupport.line(Money.inr(100), 1)), Money.inr(0), T0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("payment verification")
    class Payment {

        @Test
        @DisplayName("confirmation moves the order to CONFIRMED and unlocks preparation")
        void confirmationUnlocksPreparation() {
            Order order = OrderTestSupport.newOrder(OrderTestSupport.line(Money.inr(10000), 1));
            order.markPaid("pay_123", "order_123", LATER);

            assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(order.preparationStatus()).isEqualTo(PreparationStatus.AWAITING_PREPARATION);
            assertThat(order.isPaid()).isTrue();
            assertThat(order.peekDomainEvents())
                    .anyMatch(event -> event instanceof OrderConfirmed);
        }

        @Test
        @DisplayName("a duplicate confirmation is idempotent and emits no second event")
        void duplicateConfirmationIsIdempotent() {
            Order order = OrderTestSupport.newOrder(OrderTestSupport.line(Money.inr(10000), 1));
            order.markPaid("pay_123", "order_123", LATER);
            order.pullDomainEvents();

            order.markPaid("pay_123", "order_123", LATER.plusSeconds(60));

            assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("a confirmation naming a different payment is refused")
        void conflictingPaymentRefused() {
            Order order = OrderTestSupport.newOrder(OrderTestSupport.line(Money.inr(10000), 1));
            order.markPaid("pay_123", "order_123", LATER);

            assertThatThrownBy(() -> order.markPaid("pay_999", "order_999", LATER))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                    .isEqualTo(ErrorCode.PAYMENT_ALREADY_CAPTURED);
        }

        @Test
        @DisplayName("a late failure does not un-pay a confirmed order")
        void lateFailureIsIgnored() {
            Order order = OrderTestSupport.newOrder(OrderTestSupport.line(Money.inr(10000), 1));
            order.markPaid("pay_123", "order_123", LATER);

            order.markPaymentFailed("provider reported a late failure", LATER.plusSeconds(60));

            assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("a failed payment can be retried back to pending")
        void failedCanRetry() {
            Order order = OrderTestSupport.newOrder(OrderTestSupport.line(Money.inr(10000), 1));
            order.markPaymentFailed("card declined", LATER);
            assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);

            order.allowPaymentRetry(LATER.plusSeconds(30));

            assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
            assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("retrying a non-failed payment is refused")
        void retryOnlyFromFailed() {
            Order order = OrderTestSupport.newOrder(OrderTestSupport.line(Money.inr(10000), 1));
            assertThatThrownBy(() -> order.allowPaymentRetry(LATER))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("verification-pending is recorded without confirming")
        void verificationPending() {
            Order order = OrderTestSupport.newOrder(OrderTestSupport.line(Money.inr(10000), 1));
            order.markPaymentVerificationPending("pay_1", "order_1", LATER);

            assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_VERIFICATION_PENDING);
            assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.VERIFICATION_PENDING);
            assertThat(order.isPaid()).isFalse();
        }
    }

    @Nested
    @DisplayName("made-to-order preparation")
    class Preparation {

        @Test
        @DisplayName("preparation cannot begin before payment is verified")
        void preparationRequiresPayment() {
            Order order = OrderTestSupport.newOrder(OrderTestSupport.line(Money.inr(10000), 1));

            assertThatThrownBy(() -> order.startPreparation("admin", LATER))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("payment is verified");
            assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        }

        @Test
        @DisplayName("the full paid lifecycle advances step by step")
        void fullLifecycle() {
            Order order = paidOrder();

            order.startPreparation("admin", LATER);
            assertThat(order.status()).isEqualTo(OrderStatus.PREPARATION_STARTED);
            assertThat(order.preparationStatus()).isEqualTo(PreparationStatus.STARTED);

            order.completePreparation("admin", LATER);
            assertThat(order.status()).isEqualTo(OrderStatus.PREPARATION_COMPLETED);

            order.startPackaging("admin", LATER);
            assertThat(order.status()).isEqualTo(OrderStatus.PACKAGING);

            order.markReadyForDispatch("admin", LATER);
            assertThat(order.status()).isEqualTo(OrderStatus.READY_FOR_DISPATCH);
        }

        @Test
        @DisplayName("preparation can be held and resumed")
        void holdAndResume() {
            Order order = paidOrder();
            order.startPreparation("admin", LATER);

            order.holdPreparation("out of cocoa", "admin", LATER);
            assertThat(order.status()).isEqualTo(OrderStatus.PREPARATION_ON_HOLD);

            order.resumePreparation("admin", LATER.plusSeconds(60));
            assertThat(order.status()).isEqualTo(OrderStatus.PREPARATION_STARTED);
        }

        @Test
        @DisplayName("completing preparation requires it to have started")
        void completeRequiresStarted() {
            Order order = paidOrder();
            assertThatThrownBy(() -> order.completePreparation("admin", LATER))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("packaging cannot begin before preparation completes")
        void packagingRequiresCompletion() {
            Order order = paidOrder();
            order.startPreparation("admin", LATER);
            assertThatThrownBy(() -> order.startPackaging("admin", LATER))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("resuming an order that is not on hold is refused")
        void resumeRequiresHold() {
            Order order = paidOrder();
            assertThatThrownBy(() -> order.resumePreparation("admin", LATER))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    @DisplayName("seller-managed delivery")
    class Delivery {

        @Test
        @DisplayName("delivery is refused until the order is ready for dispatch")
        void deliveryRequiresReady() {
            Order order = paidOrder();
            assertThatThrownBy(() -> order.markOutForDelivery("admin", LATER))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("assigning delivery records the person and moves the order out for delivery")
        void assignDelivery() {
            Order order = readyForDispatch();
            order.assignDelivery("Ravi", "9876500000", "admin", LATER);

            assertThat(order.status()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
            assertThat(order.deliveryStatus()).isEqualTo(DeliveryStatus.OUT_FOR_DELIVERY);
            assertThat(order.deliveryPersonName()).isEqualTo("Ravi");
        }

        @Test
        @DisplayName("a delivered order records the delivery timestamp")
        void deliveredRecordsTimestamp() {
            Order order = readyForDispatch();
            order.markOutForDelivery("admin", LATER);
            order.markDelivered("admin", LATER.plusSeconds(600));

            assertThat(order.status()).isEqualTo(OrderStatus.DELIVERED);
            assertThat(order.deliveredAt()).isEqualTo(LATER.plusSeconds(600));
        }

        @Test
        @DisplayName("a failed attempt can be rescheduled and then delivered")
        void failedThenRescheduledThenDelivered() {
            Order order = readyForDispatch();
            order.markOutForDelivery("admin", LATER);

            order.recordFailedDeliveryAttempt("nobody home", "admin", LATER);
            assertThat(order.status()).isEqualTo(OrderStatus.DELIVERY_ATTEMPT_FAILED);

            order.rescheduleDelivery("admin", LATER);
            assertThat(order.status()).isEqualTo(OrderStatus.DELIVERY_RESCHEDULED);

            order.markOutForDelivery("admin", LATER);
            assertThat(order.status()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);

            order.markDelivered("admin", LATER);
            assertThat(order.status()).isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("a failed delivery can be returned")
        void failedThenReturned() {
            Order order = readyForDispatch();
            order.markOutForDelivery("admin", LATER);
            order.recordFailedDeliveryAttempt("customer refused", "admin", LATER);
            order.markReturned("refused", "admin", LATER);

            assertThat(order.status()).isEqualTo(OrderStatus.DELIVERY_RETURNED);
            assertThat(order.deliveryStatus()).isEqualTo(DeliveryStatus.RETURNED);
        }

        @Test
        @DisplayName("a failed attempt cannot be recorded for an order still in preparation")
        void failedAttemptRequiresOutForDelivery() {
            Order order = readyForDispatch();
            assertThatThrownBy(() -> order.recordFailedDeliveryAttempt("x", "admin", LATER))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("delivering an order that is not out for delivery is refused")
        void deliveredRequiresOutForDelivery() {
            Order order = readyForDispatch();
            assertThatThrownBy(() -> order.markDelivered("admin", LATER))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    @DisplayName("cancellation and refunds")
    class Cancellation {

        @Test
        @DisplayName("an unpaid order can be cancelled without a refund")
        void cancelUnpaid() {
            Order order = OrderTestSupport.newOrder(OrderTestSupport.line(Money.inr(10000), 1));
            order.cancel("changed my mind", OrderTestSupport.CUSTOMER.toString(), LATER);

            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(order.peekDomainEvents()).anyMatch(e -> e instanceof OrderCancelled);
        }

        @Test
        @DisplayName("cancelling a paid order flags the payment for refund review")
        void cancelPaid() {
            Order order = paidOrder();
            order.cancel("customer request", OrderTestSupport.CUSTOMER.toString(), LATER);

            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.REFUND_UNDER_REVIEW);
        }

        @Test
        @DisplayName("an order out for delivery cannot be cancelled")
        void cannotCancelOutForDelivery() {
            Order order = readyForDispatch();
            order.markOutForDelivery("admin", LATER);

            assertThatThrownBy(() -> order.cancel("too late", "admin", LATER))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("refund");
        }

        @Test
        @DisplayName("a delivered order cannot be cancelled")
        void cannotCancelDelivered() {
            Order order = readyForDispatch();
            order.markOutForDelivery("admin", LATER);
            order.markDelivered("admin", LATER);

            assertThatThrownBy(() -> order.cancel("x", "admin", LATER))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("a refund can be requested for a paid order and moves it under review")
        void requestRefundPaid() {
            Order order = paidOrder();
            order.requestRefund("not as expected", LATER);

            assertThat(order.status()).isEqualTo(OrderStatus.REFUND_UNDER_REVIEW);
            assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.REFUND_UNDER_REVIEW);
            assertThat(order.peekDomainEvents()).anyMatch(e -> e instanceof RefundRequested);
        }

        @Test
        @DisplayName("a refund cannot be requested for an unpaid order")
        void refundRequiresPayment() {
            Order order = OrderTestSupport.newOrder(OrderTestSupport.line(Money.inr(10000), 1));
            assertThatThrownBy(() -> order.requestRefund("x", LATER))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("a second refund request is refused")
        void refundNotRequestedTwice() {
            Order order = paidOrder();
            order.requestRefund("x", LATER);
            assertThatThrownBy(() -> order.requestRefund("x again", LATER))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("marking refunded moves the order to REFUNDED and is idempotent")
        void markRefunded() {
            Order order = paidOrder();
            order.requestRefund("x", LATER);
            order.markRefunded(LATER);

            assertThat(order.status()).isEqualTo(OrderStatus.REFUNDED);
            assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.REFUNDED);

            order.markRefunded(LATER.plusSeconds(10));
            assertThat(order.status()).isEqualTo(OrderStatus.REFUNDED);
        }

        @Test
        @DisplayName("refunding a cancelled order keeps it cancelled")
        void refundOfCancelledStaysCancelled() {
            Order order = paidOrder();
            order.cancel("customer request", OrderTestSupport.CUSTOMER.toString(), LATER);
            order.markRefunded(LATER.plusSeconds(60));

            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        }
    }

    @Nested
    @DisplayName("ownership, notes and history")
    class Miscellaneous {

        @Test
        @DisplayName("a customer can only act on their own order")
        void ownershipEnforced() {
            Order order = OrderTestSupport.newOrder(OrderTestSupport.line(Money.inr(10000), 1));

            order.assertOwnedBy(OrderTestSupport.CUSTOMER);

            assertThatThrownBy(() -> order.assertOwnedBy(UUID.randomUUID()))
                    .isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> order.assertOwnedBy(null))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("notes are appended and ordered by time")
        void notesAppended() {
            Order order = paidOrder();
            order.addNote("first", "admin", LATER);
            order.addNote("second", "admin", LATER.plusSeconds(10));

            assertThat(order.notes()).hasSize(2);
            assertThat(order.notes().get(0).text()).isEqualTo("first");
            assertThat(order.notes().get(1).text()).isEqualTo("second");
        }

        @Test
        @DisplayName("every transition appends to the status history and raises an event")
        void historyAndEventsAccumulate() {
            Order order = paidOrder();
            order.pullDomainEvents();
            order.startPreparation("admin", LATER);
            order.completePreparation("admin", LATER);

            assertThat(order.statusHistory()).hasSize(4); // placed + confirmed + started + completed
            assertThat(order.pullDomainEvents())
                    .hasSize(2)
                    .allMatch(event -> event instanceof OrderStatusChanged);
        }

        @Test
        @DisplayName("a transition to the same status is a no-op")
        void sameStatusIsNoOp() {
            Order order = paidOrder();
            order.pullDomainEvents();
            int before = order.statusHistory().size();
            // Replay the same confirmation: a redelivered webhook must not append a second history entry.
            order.markPaid("pay_1", "order_1", LATER);
            assertThat(order.statusHistory()).hasSize(before);
        }
    }

    private static Order paidOrder() {
        Order order = OrderTestSupport.newOrder(OrderTestSupport.line(Money.inr(10000), 1));
        order.markPaid("pay_1", "order_1", LATER);
        return order;
    }

    private static Order readyForDispatch() {
        Order order = paidOrder();
        order.startPreparation("admin", LATER);
        order.completePreparation("admin", LATER);
        order.startPackaging("admin", LATER);
        order.markReadyForDispatch("admin", LATER);
        return order;
    }
}
