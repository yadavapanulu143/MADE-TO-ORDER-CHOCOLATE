package com.choco.fulfilment.domain.delivery;

import com.choco.core.domain.Money;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import java.time.Instant;
import java.util.UUID;

/** Shared fixtures for the delivery aggregate tests. */
final class DeliveryTestSupport {

    static final Instant T0 = Instant.parse("2026-02-01T09:00:00Z");
    static final Instant LATER = T0.plusSeconds(600);
    static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID CUSTOMER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private DeliveryTestSupport() {
    }

    static DeliveryAddress address() {
        return new DeliveryAddress("Asha Rao", PhoneNumber.of("9876543210"), "12 Cocoa Lane", null,
                "Bengaluru", "Karnataka", Pincode.of("560001"), "Leave at the door");
    }

    static Delivery openDelivery() {
        return Delivery.open(UUID.randomUUID(), ORDER_ID, "CHOCO-2026001-1", CUSTOMER_ID, address(),
                Money.inr(5000), T0);
    }

    static Delivery assignedDelivery() {
        Delivery delivery = openDelivery();
        delivery.assign("Ravi", "9000000000", "seller", LATER);
        delivery.pullDomainEvents();
        return delivery;
    }

    static Delivery outForDelivery() {
        Delivery delivery = assignedDelivery();
        delivery.markOutForDelivery("seller", LATER);
        delivery.pullDomainEvents();
        return delivery;
    }
}
