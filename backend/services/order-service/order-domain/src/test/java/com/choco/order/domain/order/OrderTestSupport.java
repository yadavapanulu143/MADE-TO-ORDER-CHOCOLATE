package com.choco.order.domain.order;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.Money;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import com.choco.core.domain.Quantity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Shared fixtures for the order domain tests. */
final class OrderTestSupport {

    static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    static final UUID CUSTOMER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID PRODUCT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID VARIANT = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private OrderTestSupport() {
    }

    static OrderLine line(Money unitPrice, int quantity) {
        return new OrderLine(PRODUCT, VARIANT, "Dark Chocolate", "200 g", "SKU-1", unitPrice,
                Quantity.of(quantity));
    }

    static OrderLine line(UUID variantId, Money unitPrice, int quantity) {
        return new OrderLine(PRODUCT, variantId, "Dark Chocolate", "200 g", "SKU-1", unitPrice,
                Quantity.of(quantity));
    }

    static CustomerSnapshot customer() {
        return new CustomerSnapshot("Asha Rao", EmailAddress.of("asha@example.com"),
                PhoneNumber.of("9876543210"));
    }

    static DeliveryAddress address() {
        return new DeliveryAddress("Asha Rao", PhoneNumber.of("9876543210"), "12 Cocoa Lane", "Flat 3",
                "Bengaluru", "Karnataka", Pincode.of("560001"), "Ring the bell");
    }

    static Order newOrder(OrderLine... lines) {
        return Order.place(UUID.randomUUID(), "CHOCO-1001", CUSTOMER, customer(), address(),
                List.of(lines), Money.inr(5000), T0);
    }
}
