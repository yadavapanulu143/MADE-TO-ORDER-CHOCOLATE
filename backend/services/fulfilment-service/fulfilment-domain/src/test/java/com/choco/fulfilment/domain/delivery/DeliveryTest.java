package com.choco.fulfilment.domain.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.BusinessRuleException;
import com.choco.fulfilment.domain.event.DeliveryDelivered;
import com.choco.fulfilment.domain.event.DeliveryFailed;
import com.choco.fulfilment.domain.event.OutForDelivery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Delivery")
class DeliveryTest {

    @Test
    @DisplayName("a delivery opens pending, with nobody assigned and no event")
    void opensPending() {
        Delivery delivery = DeliveryTestSupport.openDelivery();

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.deliveryPersonName()).isNull();
        assertThat(delivery.pincode().value()).isEqualTo("560001");
        assertThat(delivery.peekDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("assigning records who will deliver, without dispatching")
    void assignRecordsWithoutDispatching() {
        Delivery delivery = DeliveryTestSupport.openDelivery();
        delivery.assign("Ravi", "9000000000", "seller", DeliveryTestSupport.LATER);

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(delivery.deliveryPersonName()).isEqualTo("Ravi");
        assertThat(delivery.dispatchedAt()).isNull();
    }

    @Test
    @DisplayName("an assignment is refused once the parcel has left")
    void cannotReassignAfterDispatch() {
        Delivery delivery = DeliveryTestSupport.outForDelivery();

        assertThatThrownBy(() -> delivery.assign("Someone", null, "seller", DeliveryTestSupport.LATER))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("re-assignment before dispatch is allowed")
    void canReassignBeforeDispatch() {
        Delivery delivery = DeliveryTestSupport.assignedDelivery();
        delivery.assign("Sita", "9111111111", "seller", DeliveryTestSupport.LATER);

        assertThat(delivery.deliveryPersonName()).isEqualTo("Sita");
        assertThat(delivery.status()).isEqualTo(DeliveryStatus.ASSIGNED);
    }

    @Test
    @DisplayName("an assignment needs a name")
    void assignmentNeedsAName() {
        Delivery delivery = DeliveryTestSupport.openDelivery();

        assertThatThrownBy(() -> delivery.assign("  ", null, "seller", DeliveryTestSupport.LATER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("going out raises an out-for-delivery event and stamps the dispatch time")
    void outForDeliveryRaisesEvent() {
        Delivery delivery = DeliveryTestSupport.assignedDelivery();
        delivery.markOutForDelivery("seller", DeliveryTestSupport.LATER);

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.OUT_FOR_DELIVERY);
        assertThat(delivery.dispatchedAt()).isNotNull();
        assertThat(delivery.peekDomainEvents()).hasAtLeastOneElementOfType(OutForDelivery.class);
    }

    @Test
    @DisplayName("a delivery cannot go out before it is assigned")
    void cannotGoOutUnassigned() {
        Delivery delivery = DeliveryTestSupport.openDelivery();

        assertThatThrownBy(() -> delivery.markOutForDelivery("seller", DeliveryTestSupport.LATER))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("delivery without proof is accepted when the seller has not required it")
    void deliveredWithoutProofWhenNotRequired() {
        Delivery delivery = DeliveryTestSupport.outForDelivery();
        delivery.markDelivered(null, "handed to the customer", false, "seller", DeliveryTestSupport.LATER);

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.deliveredAt()).isNotNull();
        assertThat(delivery.proofReference()).isNull();
        assertThat(delivery.peekDomainEvents()).hasAtLeastOneElementOfType(DeliveryDelivered.class);
    }

    @Test
    @DisplayName("when proof is required, delivered without it is refused")
    void proofRequired() {
        Delivery delivery = DeliveryTestSupport.outForDelivery();

        assertThatThrownBy(() -> delivery.markDelivered(null, null, true, "seller",
                DeliveryTestSupport.LATER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Proof of delivery is required");

        delivery.markDelivered("signed by Asha", null, true, "seller", DeliveryTestSupport.LATER);
        assertThat(delivery.proofReference()).isEqualTo("signed by Asha");
    }

    @Test
    @DisplayName("a failed attempt records its reason and raises an event")
    void failedAttempt() {
        Delivery delivery = DeliveryTestSupport.outForDelivery();
        delivery.recordFailedAttempt("nobody home", "will retry tomorrow", "seller",
                DeliveryTestSupport.LATER);

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.ATTEMPT_FAILED);
        assertThat(delivery.peekDomainEvents()).hasAtLeastOneElementOfType(DeliveryFailed.class);
        assertThat(delivery.attempts()).hasSize(1);
        assertThat(delivery.attempts().getFirst().failureReason()).isEqualTo("nobody home");
    }

    @Test
    @DisplayName("a reschedule records another attempt")
    void rescheduleRecordsAnAttempt() {
        Delivery delivery = DeliveryTestSupport.outForDelivery();
        delivery.recordFailedAttempt("nobody home", null, "seller", DeliveryTestSupport.LATER);
        delivery.reschedule("second try tomorrow", "seller", DeliveryTestSupport.LATER);

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.RESCHEDULED);
        assertThat(delivery.attemptCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a returned parcel is terminal and carries the reason")
    void returned() {
        Delivery delivery = DeliveryTestSupport.outForDelivery();
        delivery.recordFailedAttempt("customer refused", null, "seller", DeliveryTestSupport.LATER);
        delivery.markReturned("customer refused", "seller", DeliveryTestSupport.LATER);

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.RETURNED);
        assertThat(delivery.status().isTerminal()).isTrue();
    }

    @Test
    @DisplayName("a delivery can be cancelled while it is still with the seller")
    void cancelBeforeDispatch() {
        Delivery delivery = DeliveryTestSupport.assignedDelivery();
        delivery.cancel("order cancelled", "seller", DeliveryTestSupport.LATER);

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.CANCELLED);
    }

    @Test
    @DisplayName("proof can be attached to a delivered order and nowhere else")
    void proofOnlyOnDelivered() {
        Delivery delivery = DeliveryTestSupport.assignedDelivery();

        assertThatThrownBy(() -> delivery.recordProof("photo.jpg", DeliveryTestSupport.LATER))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("attempts are numbered from one, in order")
    void attemptsAreNumbered() {
        Delivery delivery = DeliveryTestSupport.outForDelivery();
        delivery.recordFailedAttempt("first", null, "seller", DeliveryTestSupport.LATER);
        delivery.reschedule(null, "seller", DeliveryTestSupport.LATER);
        delivery.markOutForDelivery("seller", DeliveryTestSupport.LATER);
        delivery.markDelivered(null, null, false, "seller", DeliveryTestSupport.LATER);

        assertThat(delivery.attempts()).extracting(DeliveryAttempt::attemptNumber)
                .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("a rehydrated delivery does not re-raise events")
    void rehydrateIsQuiet() {
        Delivery delivery = Delivery.rehydrate(java.util.UUID.randomUUID(),
                DeliveryTestSupport.ORDER_ID,
                "CHOCO-2026001-1", DeliveryTestSupport.CUSTOMER_ID, DeliveryTestSupport.address(),
                com.choco.core.domain.Money.inr(5000), DeliveryStatus.DELIVERED, "Ravi", "9000000000",
                DeliveryTestSupport.T0, DeliveryTestSupport.LATER, "signed", DeliveryTestSupport.T0,
                DeliveryTestSupport.LATER);

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.proofReference()).isEqualTo("signed");
        assertThat(delivery.peekDomainEvents()).isEmpty();
    }
}
