package com.choco.order.domain.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The lifecycle table is the single source of truth for what moves are legal, so it is tested
 * exhaustively: every status is checked for a terminal/non-terminal classification and the transitions
 * that matter to the made-to-order rule are asserted directly.
 */
@DisplayName("Order lifecycle table")
class OrderStatusTest {

    @Test
    @DisplayName("a pending order cannot be marked as in preparation")
    void pendingCannotSkipToPreparation() {
        assertThat(OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.PREPARATION_STARTED)).isFalse();
        assertThat(OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.PREPARATION_COMPLETED)).isFalse();
        assertThat(OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.PACKAGING)).isFalse();
        assertThat(OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.OUT_FOR_DELIVERY)).isFalse();
        assertThat(OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    @DisplayName("only a confirmed order releases preparation")
    void confirmedUnlocksPreparation() {
        assertThat(OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PREPARATION_STARTED)).isTrue();
    }

    @Test
    @DisplayName("a failed payment can be retried or cancelled, not confirmed directly")
    void failedPaymentPaths() {
        assertThat(OrderStatus.PAYMENT_FAILED.canTransitionTo(OrderStatus.PAYMENT_PENDING)).isTrue();
        assertThat(OrderStatus.PAYMENT_FAILED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PAYMENT_FAILED.canTransitionTo(OrderStatus.CONFIRMED)).isFalse();
    }

    @Test
    @DisplayName("cancelled and refunded are terminal")
    void terminalStatuses() {
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.REFUNDED.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.allowedNext()).isEmpty();
        assertThat(OrderStatus.REFUNDED.allowedNext()).isEmpty();
    }

    @Test
    @DisplayName("an order out for delivery cannot be cancelled, only delivered or failed")
    void outForDeliveryPaths() {
        assertThat(OrderStatus.OUT_FOR_DELIVERY.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
        assertThat(OrderStatus.OUT_FOR_DELIVERY.canTransitionTo(OrderStatus.DELIVERY_ATTEMPT_FAILED)).isTrue();
        assertThat(OrderStatus.OUT_FOR_DELIVERY.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    @DisplayName("a delivered order cannot go back to delivery")
    void deliveredIsStable() {
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.OUT_FOR_DELIVERY)).isFalse();
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.REFUND_UNDER_REVIEW)).isTrue();
    }

    @Test
    @DisplayName("a null target is never a legal transition")
    void nullIsRefused() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.canTransitionTo(null)).isFalse();
        }
    }

    @Test
    @DisplayName("paid statuses report isPaid, unpaid ones do not")
    void paidClassification() {
        assertThat(OrderStatus.PAYMENT_PENDING.isPaid()).isFalse();
        assertThat(OrderStatus.PAYMENT_VERIFICATION_PENDING.isPaid()).isFalse();
        assertThat(OrderStatus.PAYMENT_FAILED.isPaid()).isFalse();
        assertThat(OrderStatus.CANCELLED.isPaid()).isFalse();
        assertThat(OrderStatus.CONFIRMED.isPaid()).isTrue();
        assertThat(OrderStatus.PREPARATION_STARTED.isPaid()).isTrue();
        assertThat(OrderStatus.DELIVERED.isPaid()).isTrue();
        assertThat(OrderStatus.REFUNDED.isPaid()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    @DisplayName("no status allows a transition to itself")
    void noSelfTransitions(OrderStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @Test
    @DisplayName("every status has an entry in the transition table")
    void everyStatusCovered() {
        // allowedNext() reads the table; a status missing from it would return an empty set even though
        // it should have outgoing edges. Only the two terminal statuses may legitimately be empty.
        for (OrderStatus status : OrderStatus.values()) {
            if (!status.isTerminal()) {
                assertThat(status.allowedNext())
                        .as("%s should have at least one allowed transition", status)
                        .isNotEmpty();
            }
        }
        assertThat(EnumSet.allOf(OrderStatus.class)).hasSize(17);
    }
}
