package com.choco.payment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Payment status transitions")
class PaymentStatusTest {

    @Test
    @DisplayName("a refunded payment is terminal")
    void refundedIsTerminal() {
        assertThat(PaymentStatus.REFUNDED.isTerminal()).isTrue();
        assertThat(PaymentStatus.REFUNDED.allowedTransitions()).isEmpty();
    }

    @Test
    @DisplayName("only settled states count as the customer's money being held")
    void settledStates() {
        assertThat(PaymentStatus.PAID.isSettledWithSeller()).isTrue();
        assertThat(PaymentStatus.PARTIALLY_REFUNDED.isSettledWithSeller()).isTrue();
        assertThat(PaymentStatus.DISPUTED.isSettledWithSeller()).isTrue();
        assertThat(PaymentStatus.CREATED.isSettledWithSeller()).isFalse();
        assertThat(PaymentStatus.PENDING.isSettledWithSeller()).isFalse();
        assertThat(PaymentStatus.FAILED.isSettledWithSeller()).isFalse();
    }

    @Test
    @DisplayName("paid money cannot silently return to pending")
    void paidCannotReturnToPending() {
        assertThat(PaymentStatus.PAID.canTransitionTo(PaymentStatus.PENDING)).isFalse();
    }

    @Test
    @DisplayName("a state can always transition to itself, for an idempotent write")
    void selfTransitionAllowed() {
        for (PaymentStatus status : PaymentStatus.values()) {
            assertThat(status.canTransitionTo(status)).isTrue();
        }
    }
}
