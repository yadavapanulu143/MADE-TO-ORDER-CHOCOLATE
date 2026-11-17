package com.choco.payment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.payment.domain.event.DisputeRaised;
import com.choco.payment.domain.event.PaymentCaptured;
import com.choco.payment.domain.event.PaymentFailed;
import com.choco.payment.domain.event.PaymentPending;
import com.choco.payment.domain.event.RefundProcessed;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Payment aggregate")
class PaymentTest {

    private static final Instant T0 = PaymentTestSupport.T0;
    private static final Instant LATER = PaymentTestSupport.LATER;
    private static final Money PRICE = PaymentTestSupport.PRICE;

    private static ErrorCode codeOf(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected a BusinessRuleException");
        } catch (BusinessRuleException ex) {
            return ex.errorCode();
        }
    }

    @Nested
    @DisplayName("opening a payment")
    class Opening {

        @Test
        @DisplayName("starts created and unpaid, so nothing can be prepared yet")
        void startsCreated() {
            Payment payment = PaymentTestSupport.openPayment();

            assertThat(payment.status()).isEqualTo(PaymentStatus.CREATED);
            assertThat(payment.isPaid()).isFalse();
            assertThat(payment.providerPaymentId()).isNull();
            assertThat(payment.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("refuses a non-positive amount")
        void refusesNonPositiveAmount() {
            assertThatThrownBy(() -> Payment.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    Money.inr(0), "r", "razorpay", "po", T0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("capture")
    class Capture {

        @Test
        @DisplayName("moves to paid and raises exactly one captured event")
        void capturesAndRaisesEvent() {
            Payment payment = PaymentTestSupport.openPayment();

            payment.markCaptured("pay_1", PaymentMethod.CARD, PRICE, LATER);

            assertThat(payment.status()).isEqualTo(PaymentStatus.PAID);
            assertThat(payment.isPaid()).isTrue();
            assertThat(payment.method()).isEqualTo(PaymentMethod.CARD);
            assertThat(payment.attempts()).hasSize(1);
            assertThat(payment.pullDomainEvents())
                    .singleElement()
                    .isInstanceOfSatisfying(PaymentCaptured.class, event -> {
                        assertThat(event.providerPaymentId()).isEqualTo("pay_1");
                        assertThat(event.amountMinorUnits()).isEqualTo(PRICE.minorUnits());
                        assertThat(event.orderId()).isEqualTo(payment.orderId().toString());
                    });
        }

        @Test
        @DisplayName("is idempotent for a repeated delivery of the same payment")
        void idempotentForSamePayment() {
            Payment payment = PaymentTestSupport.paidPayment();

            payment.markCaptured("pay_1", PaymentMethod.CARD, PRICE, LATER.plusSeconds(30));

            assertThat(payment.status()).isEqualTo(PaymentStatus.PAID);
            assertThat(payment.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("refuses a different payment id against an already-paid payment")
        void refusesSecondPayment() {
            Payment payment = PaymentTestSupport.paidPayment();

            assertThat(codeOf(() -> payment.markCaptured("pay_2", PaymentMethod.UPI, PRICE, LATER)))
                    .isEqualTo(ErrorCode.PAYMENT_ALREADY_CAPTURED);
        }

        @Test
        @DisplayName("refuses a capture whose amount does not match what was charged")
        void refusesMismatchedAmount() {
            Payment payment = PaymentTestSupport.openPayment();

            assertThat(codeOf(() -> payment.markCaptured("pay_1", PaymentMethod.UPI, Money.inr(100), LATER)))
                    .isEqualTo(ErrorCode.PAYMENT_VERIFICATION_FAILED);
        }

        @Test
        @DisplayName("refuses a capture after money was already returned")
        void refusesAfterRefund() {
            Payment payment = PaymentTestSupport.paidPayment();
            UUID refundId = UUID.randomUUID();
            payment.requestRefund(refundId, PRICE, "goodwill", "admin", LATER);
            payment.markRefundProcessed(refundId, "rfnd_1", LATER);

            assertThat(codeOf(() -> payment.markCaptured("pay_1", PaymentMethod.UPI, PRICE, LATER)))
                    .isEqualTo(ErrorCode.PAYMENT_ALREADY_CAPTURED);
        }
    }

    @Nested
    @DisplayName("pending and failure")
    class PendingAndFailure {

        @Test
        @DisplayName("a pending attempt records an attempt and does not mark the order paid")
        void pendingRecordsAttempt() {
            Payment payment = PaymentTestSupport.openPayment();

            payment.markPending("pay_1", PaymentMethod.NETBANKING, LATER);

            assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.isPaid()).isFalse();
            assertThat(payment.attempts()).hasSize(1);
            assertThat(payment.pullDomainEvents()).singleElement().isInstanceOf(PaymentPending.class);
        }

        @Test
        @DisplayName("a failure records the reason and raises a failed event")
        void failureRecordsReason() {
            Payment payment = PaymentTestSupport.openPayment();

            payment.markFailed("pay_1", PaymentMethod.UPI, "insufficient funds", LATER);

            assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.failureReason()).isEqualTo("insufficient funds");
            assertThat(payment.pullDomainEvents()).singleElement().isInstanceOf(PaymentFailed.class);
        }

        @Test
        @DisplayName("a retry after failure is allowed against the same provider order")
        void retryAfterFailure() {
            Payment payment = PaymentTestSupport.openPayment();
            payment.markFailed("pay_1", PaymentMethod.UPI, "timed out", LATER);

            payment.markCaptured("pay_2", PaymentMethod.CARD, PRICE, LATER.plusSeconds(60));

            assertThat(payment.status()).isEqualTo(PaymentStatus.PAID);
            assertThat(payment.attempts()).hasSize(2);
        }

        @Test
        @DisplayName("a stale failure delivery does not undo a completed capture")
        void staleFailureIgnored() {
            Payment payment = PaymentTestSupport.paidPayment();

            payment.markFailed("pay_1", PaymentMethod.UPI, "stale", LATER.plusSeconds(30));

            assertThat(payment.status()).isEqualTo(PaymentStatus.PAID);
        }
    }

    @Nested
    @DisplayName("refunds")
    class Refunds {

        @Test
        @DisplayName("a full refund moves the payment to refunded")
        void fullRefund() {
            Payment payment = PaymentTestSupport.paidPayment();
            UUID refundId = UUID.randomUUID();

            payment.requestRefund(refundId, PRICE, "customer request", "admin", LATER);
            payment.markRefundProcessed(refundId, "rfnd_1", LATER);

            assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(payment.refundedAmount()).isEqualTo(PRICE);
            assertThat(payment.pullDomainEvents())
                    .singleElement()
                    .isInstanceOfSatisfying(RefundProcessed.class,
                            event -> assertThat(event.fullRefund()).isTrue());
        }

        @Test
        @DisplayName("a partial refund moves the payment to partially refunded")
        void partialRefund() {
            Payment payment = PaymentTestSupport.paidPayment();
            UUID refundId = UUID.randomUUID();

            payment.requestRefund(refundId, Money.inr(20000), "partial", "admin", LATER);
            payment.markRefundProcessed(refundId, "rfnd_1", LATER);

            assertThat(payment.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
            assertThat(payment.refundedAmount()).isEqualTo(Money.inr(20000));
        }

        @Test
        @DisplayName("two refunds may not together exceed what was captured")
        void refundsCannotExceedCaptured() {
            Payment payment = PaymentTestSupport.paidPayment();
            UUID first = UUID.randomUUID();
            payment.requestRefund(first, Money.inr(30000), "partial", "admin", LATER);
            payment.markRefundProcessed(first, "rfnd_1", LATER);

            assertThat(codeOf(() -> payment.requestRefund(UUID.randomUUID(), Money.inr(30000),
                    "too much", "admin", LATER))).isEqualTo(ErrorCode.VALIDATION_FAILED);
        }

        @Test
        @DisplayName("an unpaid payment cannot be refunded")
        void unpaidCannotBeRefunded() {
            Payment payment = PaymentTestSupport.openPayment();

            assertThat(codeOf(() -> payment.requestRefund(UUID.randomUUID(), PRICE, "no", "admin", LATER)))
                    .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
        }

        @Test
        @DisplayName("a repeated processed confirmation changes nothing")
        void processedRefundIdempotent() {
            Payment payment = PaymentTestSupport.paidPayment();
            UUID refundId = UUID.randomUUID();
            payment.requestRefund(refundId, PRICE, "full", "admin", LATER);
            payment.markRefundProcessed(refundId, "rfnd_1", LATER);
            payment.pullDomainEvents();

            payment.markRefundProcessed(refundId, "rfnd_1", LATER.plusSeconds(10));

            assertThat(payment.pullDomainEvents()).isEmpty();
            assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
        }

        @Test
        @DisplayName("a failed refund records the reason and leaves the payment paid")
        void failedRefund() {
            Payment payment = PaymentTestSupport.paidPayment();
            UUID refundId = UUID.randomUUID();
            payment.requestRefund(refundId, PRICE, "full", "admin", LATER);

            payment.markRefundFailed(refundId, "provider declined", LATER);

            assertThat(payment.refund(refundId)).get()
                    .extracting(Refund::status, Refund::failureReason)
                    .containsExactly(RefundStatus.FAILED, "provider declined");
            assertThat(payment.status()).isEqualTo(PaymentStatus.PAID);
            assertThat(payment.refundedAmount()).isEqualTo(Money.inr(0));
        }
    }

    @Nested
    @DisplayName("disputes")
    class Disputes {

        @Test
        @DisplayName("a dispute moves a paid payment to disputed and raises an event")
        void disputeOnPaid() {
            Payment payment = PaymentTestSupport.paidPayment();

            payment.raiseDispute(UUID.randomUUID(), "disp_1", PRICE, "fraud", LATER);

            assertThat(payment.status()).isEqualTo(PaymentStatus.DISPUTED);
            assertThat(payment.disputes()).hasSize(1);
            assertThat(payment.pullDomainEvents()).singleElement().isInstanceOf(DisputeRaised.class);
        }

        @Test
        @DisplayName("an unpaid payment cannot be disputed")
        void disputeOnUnpaidRefused() {
            Payment payment = PaymentTestSupport.openPayment();

            assertThat(codeOf(() -> payment.raiseDispute(UUID.randomUUID(), "disp_1", PRICE, "fraud", LATER)))
                    .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
        }
    }

    @Nested
    @DisplayName("rehydration")
    class Rehydration {

        @Test
        @DisplayName("restores state without raising events")
        void restoresWithoutEvents() {
            UUID id = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();

            Payment payment = Payment.rehydrate(id, orderId, customerId, PRICE, "rcpt", "razorpay",
                    "po_1", PaymentStatus.PAID, "pay_1", PaymentMethod.UPI, null, T0, LATER);

            assertThat(payment.id()).isEqualTo(id);
            assertThat(payment.status()).isEqualTo(PaymentStatus.PAID);
            assertThat(payment.providerPaymentId()).isEqualTo("pay_1");
            assertThat(payment.pullDomainEvents()).isEmpty();
        }
    }
}
