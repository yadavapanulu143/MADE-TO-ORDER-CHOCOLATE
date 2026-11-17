package com.choco.fulfilment.domain.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Delivery status transitions")
class DeliveryStatusTest {

    @Test
    @DisplayName("a delivery cannot be delivered before it went out")
    void cannotDeliverFromPending() {
        assertThat(DeliveryStatus.PENDING.canTransitionTo(DeliveryStatus.DELIVERED)).isFalse();
        assertThat(DeliveryStatus.ASSIGNED.canTransitionTo(DeliveryStatus.DELIVERED)).isFalse();
    }

    @Test
    @DisplayName("a delivery cannot transition to itself")
    void noSelfTransition() {
        for (DeliveryStatus status : DeliveryStatus.values()) {
            assertThat(status.canTransitionTo(status))
                    .as("%s must not transition to itself", status)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("a null target is refused")
    void nullTargetRefused() {
        assertThat(DeliveryStatus.PENDING.canTransitionTo(null)).isFalse();
    }

    @Test
    @DisplayName("a failed attempt can be rescheduled or returned, never delivered directly")
    void afterFailure() {
        assertThat(DeliveryStatus.ATTEMPT_FAILED.canTransitionTo(DeliveryStatus.RESCHEDULED)).isTrue();
        assertThat(DeliveryStatus.ATTEMPT_FAILED.canTransitionTo(DeliveryStatus.RETURNED)).isTrue();
        assertThat(DeliveryStatus.ATTEMPT_FAILED.canTransitionTo(DeliveryStatus.DELIVERED)).isFalse();
    }

    @Test
    @DisplayName("an assignment can be handed back to pending while the parcel is still with the seller")
    void reassignmentReturnsToPending() {
        assertThat(DeliveryStatus.ASSIGNED.canTransitionTo(DeliveryStatus.PENDING)).isTrue();
    }

    @Test
    @DisplayName("delivered, cancelled and returned are terminal")
    void terminalStates() {
        Set<DeliveryStatus> terminal = EnumSet.of(DeliveryStatus.DELIVERED, DeliveryStatus.CANCELLED,
                DeliveryStatus.RETURNED);
        for (DeliveryStatus status : DeliveryStatus.values()) {
            assertThat(status.isTerminal())
                    .as("%s terminality", status)
                    .isEqualTo(terminal.contains(status));
        }
    }

    @Test
    @DisplayName("only an out-for-delivery parcel can fail or be delivered")
    void onlyOutForDeliveryCanResolve() {
        assertThat(DeliveryStatus.OUT_FOR_DELIVERY.canTransitionTo(DeliveryStatus.DELIVERED)).isTrue();
        assertThat(DeliveryStatus.OUT_FOR_DELIVERY.canTransitionTo(DeliveryStatus.ATTEMPT_FAILED)).isTrue();
        assertThat(DeliveryStatus.PENDING.canTransitionTo(DeliveryStatus.ATTEMPT_FAILED)).isFalse();
    }
}
